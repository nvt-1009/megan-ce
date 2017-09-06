/*
 *  Copyright (C) 2017 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package megan.data;

import jloda.util.*;
import megan.core.Document;
import megan.core.MeganFile;
import megan.parsers.blast.*;
import megan.rma6.RMA6FileCreator;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * extract a given classification and class-ids to a new document
 * Daniel Huson, 4.2015
 */
public class ExtractToNewDocument {
    /**
     * extract all named classes in the given classsification to a new RMA6 file
     *  @param srcDoc
     * @param srcClassification
     * @param srcClassIds
     * @param tarRMA6FileName
     */
    public static void apply(Document srcDoc, String srcClassification, Collection<Integer> srcClassIds, String tarRMA6FileName, ProgressListener progress, Single<Long> totalReads) throws IOException, CanceledException {

        final long startTime = System.currentTimeMillis();

        final IConnector connector = srcDoc.getConnector();
        final String[] classifications = connector.getAllClassificationNames().clone();

        final RMA6FileCreator rma6FileCreator = new RMA6FileCreator(tarRMA6FileName, true);
        rma6FileCreator.writeHeader(ProgramProperties.getProgramVersion(), srcDoc.getBlastMode(), classifications, false);
        rma6FileCreator.startAddingQueries();

        try { // user might cancel inside this block
            // determine the set of all positions to extract:
            try (IReadBlockIterator iterator = connector.getReadsIteratorForListOfClassIds(srcClassification, srcClassIds, 0, 10, true, true)) {
                progress.setTasks("Extracting", "Processing file: " + Basic.getFileNameWithoutPath(srcDoc.getMeganFile().getFileName()));
                progress.setProgress(0);
                progress.setMaximum(iterator.getMaximumProgress());

                while (iterator.hasNext()) {
                    final IReadBlock readBlock = iterator.next();
                    final StringBuilder blastTextBuf = new StringBuilder();
                    blastTextBuf.append(FileInputIterator.PREFIX_TO_INDICATE_TO_PARSE_FILENAME_STRING);
                    blastTextBuf.append("Query= ").append(readBlock.getReadHeader()).append("\n");

                    int[][] match2classification2id = new int[readBlock.getNumberOfAvailableMatchBlocks()][classifications.length];

                    for (int i = 0; i < readBlock.getNumberOfAvailableMatchBlocks(); i++) {
                        final IMatchBlock matchBlock = readBlock.getMatchBlock(i);
                        blastTextBuf.append(matchBlock.getText());
                        for (int k = 0; k < classifications.length; k++) {
                            match2classification2id[i][k] = matchBlock.getId(classifications[k]);
                        }
                    }
                    totalReads.set(totalReads.get() + 1);

                    final byte[] readBytes = (">" + readBlock.getReadHeader() + "\n" + readBlock.getReadSequence()).getBytes();
                    final byte[] matchBytes = computeSAM(srcDoc.getBlastMode(), 1000, blastTextBuf.toString());
                    rma6FileCreator.addQuery(readBytes, readBytes.length, readBlock.getNumberOfAvailableMatchBlocks(), matchBytes, matchBytes.length, match2classification2id, 0L);
                    progress.setProgress(iterator.getProgress());
                }
            }
        } finally { // finish file, whether user has canceled or not...
            rma6FileCreator.endAddingQueries();
            rma6FileCreator.writeClassifications(new String[0], null, null);
            rma6FileCreator.writeAuxBlocks(null);
            rma6FileCreator.close();

            final Document doc = new Document();
            doc.setProgressListener(progress);
            doc.getMeganFile().setFile(tarRMA6FileName, MeganFile.Type.RMA6_FILE);
            doc.getActiveViewers().addAll(Arrays.asList(classifications));
        }
        System.err.println("Extraction required " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
    }

    /**
     * compute SAM representation
     *
     * @param blastMode
     * @param matchesText
     * @return
     */
    private static byte[] computeSAM(BlastMode blastMode, int maxNumberOfReads, String matchesText) throws IOException {
        final ISAMIterator iterator;
        switch (blastMode) {
            case BlastN:
                iterator = new BlastN2SAMIterator(matchesText, maxNumberOfReads);
                break;
            case BlastP:
                iterator = new BlastP2SAMIterator(matchesText, maxNumberOfReads);
                break;
            case BlastX:
                iterator = new BlastX2SAMIterator(matchesText, maxNumberOfReads);
                break;
            default:
                throw new IOException("Invalid BLAST mode: " + blastMode.toString());
        }
        try {
            iterator.next();
            return iterator.getMatchesText();
        } finally {
            iterator.close();
        }
    }
}
