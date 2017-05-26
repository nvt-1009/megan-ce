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

package megan.dialogs.lrinspector;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import jloda.fx.ASelectionModel;
import jloda.util.Basic;
import jloda.util.Pair;
import jloda.util.ProgramProperties;
import megan.chart.ChartColorManager;
import megan.classification.Classification;
import megan.classification.ClassificationManager;
import megan.data.IMatchBlock;
import megan.fx.FXSwingUtilities;
import megan.util.Table;
import megan.util.interval.Interval;
import megan.util.interval.IntervalTree;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * pane displaying all alignments to a read
 * Created by huson on 2/23/17.
 */
public class ReadLayoutPane extends Pane {
    static public int DEFAULT_LABELED_HEIGHT = 110;

    static Font font = new Font("Courier", 10);
    private static int arrowHeight = 10;
    private final IntegerProperty preferredHeightUnlabeled = new SimpleIntegerProperty(30);
    private final IntegerProperty preferredHeightLabeled = new SimpleIntegerProperty(DEFAULT_LABELED_HEIGHT);

    private final IntervalTree<IMatchBlock> intervals;

    private final ArrayList<GeneArrow> geneArrows;
    private final String[] cNames;
    private final Set<String> classificationLabelsShowing = new HashSet<>();
    private final Group[] geneLabels;

    private final Map<IMatchBlock, GeneArrow> match2GeneArrow = new HashMap<>();

    private final ReadOnlyIntegerProperty maxReadLength;
    private final ReadOnlyDoubleProperty layoutWidth;

    private final ASelectionModel<IMatchBlock> matchSelection = new ASelectionModel<>();

    private final ReadLayoutPaneSearcher readLayoutPaneSearcher;

    private boolean hasHidden = false;

    /**
     * creates the visualization pane
     *
     * @param cNames
     * @param readLength
     * @param intervalTree
     * @param maxReadLength
     * @return
     */
    public ReadLayoutPane(final String[] cNames, final int readLength, IntervalTree<IMatchBlock> intervalTree, final ReadOnlyIntegerProperty maxReadLength, final ReadOnlyDoubleProperty layoutWidth) {
        this.intervals = intervalTree;
        this.cNames = cNames;
        this.maxReadLength = maxReadLength;
        this.layoutWidth = layoutWidth;
        readLayoutPaneSearcher = new ReadLayoutPaneSearcher(null, this, matchSelection);
        geneArrows = new ArrayList<>();
        geneLabels = new Group[cNames.length];
        for (int i = 0; i < geneLabels.length; i++) {
            geneLabels[i] = new Group();
        }

        final Table<Integer, Integer, Label>[] class2classId2Start2Label = new Table[cNames.length];

        preferredHeightUnlabeled.addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                layoutLabels();
            }
        });

        preferredHeightLabeled.addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                layoutLabels();
            }
        });

        setPrefWidth(readLength);
        setPrefHeight(30);

        final Line line = new Line();
        line.setStartX(1);
        line.setStroke(Color.DARKGRAY);
        getChildren().add(line);

        final Label lengthLabel = new Label(String.format("%,d", readLength));
        lengthLabel.setFont(font);
        lengthLabel.setTextFill(Color.DARKGRAY);
        getChildren().add(lengthLabel);

        final Set<Integer> starts = new HashSet<>();

        final Map<Pair<Integer, Integer>, GeneArrow> coordinates2geneArrow = new HashMap<>();

        for (Interval<IMatchBlock> interval : intervalTree) {
            final IMatchBlock matchBlock = interval.getData();

            final GeneArrow geneArrow;
            final boolean forward;
            if (matchBlock.getText().contains("Strand = Plus") || matchBlock.getText().contains("Strand=Plus") || matchBlock.getText().contains("Frame = +")
                    || matchBlock.getText().contains("Frame=+"))
                forward = true;
            else if (matchBlock.getText().contains("Strand = Minus") || matchBlock.getText().contains("Strand=Minus") || matchBlock.getText().contains("Frame = -") || matchBlock.getText().contains("Frame=-"))
                forward = false;
            else
                forward = (matchBlock.getAlignedQueryStart() < matchBlock.getAlignedQueryEnd());
            final int alignedQueryStart;
            final int alignedQueryEnd;
            if (forward) {
                alignedQueryStart = Math.min(matchBlock.getAlignedQueryStart(), matchBlock.getAlignedQueryEnd());
                alignedQueryEnd = Math.max(matchBlock.getAlignedQueryStart(), matchBlock.getAlignedQueryEnd());
            } else {
                alignedQueryStart = Math.max(matchBlock.getAlignedQueryStart(), matchBlock.getAlignedQueryEnd());
                alignedQueryEnd = Math.min(matchBlock.getAlignedQueryStart(), matchBlock.getAlignedQueryEnd());
            }

            final Pair<Integer, Integer> coordinates = new Pair<>(alignedQueryStart, alignedQueryEnd);
            if (coordinates2geneArrow.keySet().contains(coordinates))
                geneArrow = coordinates2geneArrow.get(coordinates);
            else {
                geneArrow = new GeneArrow(cNames, readLength, getArrowHeight(), 1, 30, coordinates.getFirst(), coordinates.getSecond(), starts);
                coordinates2geneArrow.put(coordinates, geneArrow);
                geneArrows.add(geneArrow);
                getChildren().add(geneArrow);
            }
            geneArrow.addMatchBlock(matchBlock);
            geneArrow.setFill(Color.TRANSPARENT);
            geneArrow.setStroke(Color.BLACK);
            geneArrow.setOnMousePressed(mousePressedHandler);
            geneArrow.setOnMouseClicked(mouseClickedHandler);
            geneArrow.setOnMouseReleased(mouseReleasedHandler);
            geneArrow.setOnMouseDragged(mouseDraggedHandlerY);

            match2GeneArrow.put(matchBlock, geneArrow);
        }

        matchSelection.setItems(intervalTree.values());

        widthProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                final double readWidth = (layoutWidth.get() - 60) / maxReadLength.get() * readLength;
                line.setEndX(readWidth);
                lengthLabel.setLayoutX(readWidth + 2);

                final Set<Integer> starts = new HashSet<>();
                for (final GeneArrow geneArrow : geneArrows) {
                    geneArrow.rescale(maxReadLength.get(), arrowHeight, layoutWidth.get() - 60, getHeight(), starts);
                }
                layoutLabels();
            }
        });

        heightProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                line.setStartY(0.5 * getHeight());
                line.setEndY(0.5 * getHeight());
                lengthLabel.setLayoutY(0.5 * (getHeight() - lengthLabel.getHeight()));

                final Set<Integer> starts = new HashSet<>();
                for (final GeneArrow geneArrow : geneArrows) {
                    geneArrow.rescale(maxReadLength.get(), arrowHeight, layoutWidth.get() - 60, getHeight(), starts);
                }
                layoutLabels();
            }
        });

        matchSelection.getSelectedItems().addListener(new ListChangeListener<IMatchBlock>() {
            @Override
            public void onChanged(ListChangeListener.Change<? extends IMatchBlock> c) {
                while (c.next()) {
                    for (IMatchBlock matchBlock : c.getAddedSubList()) {
                        final GeneArrow geneArrow = match2GeneArrow.get(matchBlock);
                        if (geneArrow != null) {
                            geneArrow.setEffect(new DropShadow(5, Color.RED));
                            for (Node label : geneArrow.getLabels()) {
                                label.setEffect(new DropShadow(5, Color.RED));
                            }
                        }
                    }
                    for (IMatchBlock matchBlock : c.getRemoved()) {
                        final GeneArrow geneArrow = match2GeneArrow.get(matchBlock);
                        if (geneArrow != null) {
                            geneArrow.setEffect(null);
                            for (Node label : geneArrow.getLabels()) {
                                label.setEffect(null);
                            }
                        }
                    }
                }
            }
        });
        setOnMousePressed(mousePressedHandler);
    }


    public static int getArrowHeight() {
        return arrowHeight;
    }

    public static void setArrowHeight(int arrowHeight) {
        ReadLayoutPane.arrowHeight = arrowHeight;
    }

    public ArrayList<GeneArrow> getGeneArrows() {
        return geneArrows;
    }

    /**
     * show or hide the gene labels for the given classification
     *
     * @param selectedCNames
     * @param show
     */
    public void showLabels(Collection<String> selectedCNames, boolean show) {
        if (show) {
            if (selectedCNames.size() > 0 && classificationLabelsShowing.size() == 0) {
                setPrefHeight(preferredHeightLabeled.get());
            }
            classificationLabelsShowing.addAll(selectedCNames);
        } else {
            if (classificationLabelsShowing.size() == 0)
                return; // already nothing showing
            classificationLabelsShowing.removeAll(selectedCNames);
            if (classificationLabelsShowing.size() == 0) {
                getChildren().removeAll(geneLabels);
                setPrefHeight(preferredHeightUnlabeled.get());
                return;
            }
        }

        for (int cid = 0; cid < cNames.length; cid++) {
            String cName = cNames[cid];
            if (!classificationLabelsShowing.contains(cName)) {
                if (getChildren().contains(geneLabels[cid]))
                    getChildren().remove(geneLabels[cid]);
            } else {
                if (!getChildren().contains(geneLabels[cid]))
                    getChildren().add(geneLabels[cid]);
            }
        }
    }

    public boolean isLabelsShowing(String cName) {
        return classificationLabelsShowing.contains(cName);
    }

    /**
     * layout gene labels
     */
    public void layoutLabels() {
        boolean labelsVisible = false;
        for (Group group : geneLabels) {
            if (getChildren().contains(group)) {
                labelsVisible = true;
                break;
            }
        }

        if (labelsVisible) {
            final int fontSize = (int) Math.round(font.getSize());
            setPrefHeight(preferredHeightLabeled.get());
            double centerMargin = (getPrefHeight() <= 110 ? 2 : 0.01 * getPrefHeight());
            int numberOfTracksPerDirection = Math.max(2, (int) Math.round((0.5 * preferredHeightLabeled.get() - 1.2 * ReadLayoutPane.getArrowHeight() - centerMargin) / fontSize));
            final double[] trackPos = new double[2 * numberOfTracksPerDirection];
            trackPos[numberOfTracksPerDirection - 1] = 0.5 * getPrefHeight() - 1.2 * ReadLayoutPane.getArrowHeight() - fontSize - centerMargin;
            for (int i = numberOfTracksPerDirection - 2; i >= 0; i--)
                trackPos[i] = trackPos[i + 1] - fontSize;
            trackPos[numberOfTracksPerDirection] = 0.5 * getPrefHeight() + 1.2 * ReadLayoutPane.getArrowHeight() + centerMargin;
            for (int i = numberOfTracksPerDirection + 1; i < trackPos.length; i++)
                trackPos[i] = trackPos[i - 1] + fontSize;
            final IntervalTree<Label>[] intervalTrees = new IntervalTree[trackPos.length];
            for (int i = 0; i < intervalTrees.length; i++)
                intervalTrees[i] = new IntervalTree<>();

            final double xScaleFactor = layoutWidth.get() / maxReadLength.get();

            final Map<String, ArrayList<Label>> text2OldLabels = new HashMap<>(); // recycle old labels...

            for (Group currentGeneLabels : geneLabels) {
                for (Node node : currentGeneLabels.getChildren()) {
                    if (node instanceof Label) {
                        final Label label = (Label) node;
                        ArrayList<Label> labels = text2OldLabels.get(label.getText());
                        if (labels == null) {
                            labels = new ArrayList<>();
                            text2OldLabels.put(label.getText(), labels);
                        }
                        labels.add(label);
                    }
                }
                currentGeneLabels.getChildren().clear();
            }

            for (GeneArrow geneArrow : geneArrows) {
                geneArrow.getLabels().clear();

                for (int c = 0; c < cNames.length; c++) {
                    final Group currentGeneLabels = geneLabels[c];

                    if (getChildren().contains(currentGeneLabels)) {
                        for (IMatchBlock matchBlock : geneArrow.getMatchBlocks()) {
                            int classId = matchBlock.getId(cNames[c]);
                            if (classId > 0) {
                                final String fullLabel = getClassName(c, classId).replaceAll("\\s+", " ");
                                final String abbreviatedLabel = Basic.abbreviateDotDotDot(fullLabel, 60);

                                double anchorX = xScaleFactor * geneArrow.getMiddle();
                                int estimatedPreferredLabelWidth = (int) Math.round(0.6 * fontSize * abbreviatedLabel.length());
                                double labelStartPos = Math.max(1, anchorX - 0.5 * estimatedPreferredLabelWidth);
                                final Label label = createLabel(abbreviatedLabel, fullLabel, text2OldLabels);
                                final Interval<Label> interval = new Interval<>((int) Math.round(labelStartPos), (int) Math.round(labelStartPos + estimatedPreferredLabelWidth), label);
                                if (!geneArrow.isReverse()) {
                                    for (int j = numberOfTracksPerDirection - 1; j >= 0; j--) {
                                        final Label labelToUse = processLabel(j, 0, labelStartPos, trackPos[j], interval, intervalTrees[j], currentGeneLabels);
                                        if (labelToUse != null) { // could be the one we provided or one already present
                                            geneArrow.getLabels().add(labelToUse);
                                            labelToUse.setUserData(extendByOne((IMatchBlock[]) labelToUse.getUserData(), matchBlock));
                                            if (geneArrow.isVisible())
                                                labelToUse.setVisible(true);
                                            break;
                                        }
                                    }
                                } else {
                                    for (int j = numberOfTracksPerDirection; j < trackPos.length; j++) {
                                        final Label labelToUse = processLabel(j, trackPos.length - 1, labelStartPos, trackPos[j], interval, intervalTrees[j], currentGeneLabels);
                                        if (labelToUse != null) {
                                            geneArrow.getLabels().add(labelToUse);
                                            labelToUse.setUserData(extendByOne((IMatchBlock[]) labelToUse.getUserData(), matchBlock));
                                            if (geneArrow.isVisible())
                                                labelToUse.setVisible(true);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else
            setPrefHeight(preferredHeightUnlabeled.get());
    }

    /**
     * grow the array of matches by one entry
     *
     * @param array
     * @param matchBlock
     * @return extended array
     */
    private IMatchBlock[] extendByOne(IMatchBlock[] array, IMatchBlock matchBlock) {
        if (array == null)
            return new IMatchBlock[]{matchBlock};
        else {
            final IMatchBlock[] result = new IMatchBlock[array.length + 1];
            System.arraycopy(array, 0, result, 0, array.length);
            result[result.length - 1] = matchBlock;
            return result;
        }
    }


    /**
     * process a label
     *
     * @param j
     * @param lastJ
     * @param yPos
     * @param interval
     * @param intervalTree
     * @return true, if label used
     */
    private static Label processLabel(int j, int lastJ, double xPos, double yPos, Interval<Label> interval, IntervalTree<Label> intervalTree, Group geneLabels) {
        boolean isLastOrHasNoOverlaps = (j == lastJ);
        final Collection<Interval<Label>> intervals;
        if (isLastOrHasNoOverlaps)
            intervals = null;
        else {
            intervals = intervalTree.getIntervals(interval);
            if (intervals.size() == 0)
                isLastOrHasNoOverlaps = true;
        }

        if (isLastOrHasNoOverlaps) {
            final Label label = interval.getData();
            geneLabels.getChildren().add(label);
            intervalTree.add(interval);
            label.setLayoutX(xPos);
            label.setLayoutY(yPos);
            label.setVisible(false); // will make this visible, if one of the associated gene arrows is visible
            return label; // found a place for this label...
        } else {
            for (Interval<Label> other : intervals) {
                if (other.getData().getText().equals(interval.getData().getText()))
                    return other.getData(); // label already present
            }
        }
        return null; // can't place label in this row and haven't found it either
    }

    /**
     * color arrows by bit score
     *
     * @param colorManager
     * @param maxScore
     */
    public void colorByBitScore(ChartColorManager colorManager, float maxScore) {
        setPrefHeight(preferredHeightUnlabeled.get());
        showLabels(Arrays.asList(cNames), false);

        for (GeneArrow geneArrow : geneArrows) {
            geneArrow.setFill(FXSwingUtilities.getColorFX(colorManager.getHeatMapTable().getColor(Math.round(geneArrow.getBestBitScore()), Math.round(maxScore) + 1), 0.5));
        }
    }

    /**
     * scolor arrows by normalized bit score
     *
     * @param colorManager
     * @param maxNormalizedScore
     */
    public void colorByNormalizedBitScore(ChartColorManager colorManager, float maxNormalizedScore) {
        setPrefHeight(preferredHeightUnlabeled.get());
        showLabels(Arrays.asList(cNames), false);

        for (GeneArrow geneArrow : geneArrows) {
            geneArrow.setFill(FXSwingUtilities.getColorFX(colorManager.getHeatMapTable().getColor(Math.round(1000 * geneArrow.getBestNormalizedScore()), Math.round(maxNormalizedScore) + 1), 0.5));
        }
    }


    /**
     * color gene arrows by class
     *
     * @param colorManager
     * @param cNames
     */
    public void colorByClassification(ChartColorManager colorManager, Collection<String> cNames) {

        for (GeneArrow geneArrow : geneArrows) {
            boolean colored = false;
            for (IMatchBlock matchBlock : geneArrow) {
                String keyString = "";

                for (String cName : cNames) {
                    int classId = matchBlock.getId(cName);
                    if (classId > 0) {
                        final String add = ClassificationManager.get(cName, false).getName2IdMap().get(classId);
                        keyString += (add != null ? add : cName + classId) + ";";
                    }
                }
                if (keyString.length() > 0) {
                    final Color color = FXSwingUtilities.getColorFX(colorManager.getClassColor(keyString), 0.5);
                    geneArrow.setFill(color);
                    colored = true;
                    break;
                }
            }
            if (!colored)
                geneArrow.setFill(Color.TRANSPARENT);
        }
        showLabels(cNames, true);
        layoutLabels();
    }

    private final double[] mouseDown = {0, 0};

    private final EventHandler<MouseEvent> mousePressedHandler = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent event) {
            final Node node = (Node) event.getSource();

            if (event.isPopupTrigger()) {
                if (node instanceof Label) {
                    showLabelContextMenu((Label) node, event.getScreenX(), event.getScreenY());
                    event.consume();
                } else if (node instanceof GeneArrow) {
                    ((GeneArrow) node).showContextMenu(event.getScreenX(), event.getScreenY());
                    event.consume();
                }
            } else {
                if (!event.isShiftDown())
                    matchSelection.clearSelection();

                mouseDown[0] = event.getSceneX();
                mouseDown[1] = event.getSceneY();

                final ArrayList<IMatchBlock> matchBlocks = new ArrayList<>();
                if (node.getUserData() instanceof IMatchBlock) {
                    matchBlocks.add((IMatchBlock) node.getUserData());
                } else if (node.getUserData() instanceof GeneArrow) {
                    matchBlocks.addAll(((GeneArrow) node.getUserData()).getMatchBlocks());
                } else if (node.getUserData() instanceof IMatchBlock[]) {
                    matchBlocks.addAll(Arrays.asList((IMatchBlock[]) node.getUserData()));
                } else if (node instanceof GeneArrow) {
                    matchBlocks.addAll(((GeneArrow) node).getMatchBlocks());
                }
                if (matchBlocks.size() > 0) {
                    for (IMatchBlock matchBlock : matchBlocks) {
                        matchSelection.select(matchBlock);
                    }
                    event.consume();
                }
            }
        }
    };

    private final EventHandler<MouseEvent> mouseClickedHandler = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent event) {
            final Node node = (Node) event.getSource();

            if (node instanceof GeneArrow) {
                final GeneArrow geneArrow = (GeneArrow) node;
                System.out.println(geneArrow.toString());
            } else if (node instanceof Label) {
                if (((Label) node).getTooltip() != null)
                    System.out.println(((Label) node).getTooltip().getText());
                else
                    System.out.println(((Label) node).getText());
            }
        }
    };

    private final EventHandler<MouseEvent> mouseReleasedHandler = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent event) {
            final Node node = (Node) event.getSource();

            if (event.isPopupTrigger()) {
                if (node instanceof Label) {
                    showLabelContextMenu((Label) node, event.getScreenX(), event.getScreenY());
                    event.consume();
                } else if (node instanceof GeneArrow) {
                    ((GeneArrow) node).showContextMenu(event.getScreenX(), event.getScreenY());
                    event.consume();
                }
            }
        }
    };

    private final EventHandler<MouseEvent> mouseDraggedHandler = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent event) {
            Node node = (Node) event.getSource();
            node.setLayoutX(node.getLayoutX() + (event.getSceneX() - mouseDown[0]));
            node.setLayoutY(node.getLayoutY() + (event.getSceneY() - mouseDown[1]));
            mouseDown[0] = event.getSceneX();
            mouseDown[1] = event.getSceneY();
        }
    };

    private final EventHandler<MouseEvent> mouseDraggedHandlerY = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent event) {
            Node node = (Node) event.getSource();
            node.setLayoutY(node.getLayoutY() + (event.getSceneY() - mouseDown[1]));
            mouseDown[1] = event.getSceneY();
        }
    };


    /**
     * shows the label context menu
     *
     * @param screenX
     * @param screenY
     */
    private void showLabelContextMenu(final Label label, double screenX, double screenY) {
        final ContextMenu contextMenu = new ContextMenu();
        final MenuItem selectAllSimilar = new MenuItem("Select Similar");
        selectAllSimilar.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                for (Group group : getVisibleGroups()) {
                    for (Node node : group.getChildren()) {
                        if (node instanceof Label) {
                            if (((Label) node).getText().equals(label.getText())) {
                                if (node.getUserData() instanceof IMatchBlock[]) {
                                    for (IMatchBlock matchBlock : (IMatchBlock[]) node.getUserData()) {
                                        matchSelection.select(matchBlock);
                                    }
                                }
                            }

                        }
                    }
                }
            }
        });
        contextMenu.getItems().add(selectAllSimilar);

        final MenuItem copy = new MenuItem("Copy Label");
        copy.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();
                content.putString(label.getTooltip().getText());
                clipboard.setContent(content);
            }
        });
        contextMenu.getItems().add(copy);
        contextMenu.getItems().add(new SeparatorMenuItem());

        final MenuItem webSearch = new MenuItem("Web Search...");
        webSearch.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    final String text;
                    if (label.getTooltip() != null)
                        text = label.getTooltip().getText();
                    else
                        text = label.getText();
                    final String searchURL = ProgramProperties.get(ProgramProperties.SEARCH_URL, ProgramProperties.defaultSearchURL);
                    final URL url = new URL(String.format(searchURL, text.trim().replaceAll("\\s+", "+")));
                    //System.err.println(url);
                    Basic.openWebPage(url);
                } catch (MalformedURLException e) {
                    Basic.caught(e);
                }
            }
        });
        contextMenu.getItems().add(webSearch);
        contextMenu.show(this, screenX, screenY);
    }

    public List<Group> getVisibleGroups() {
        ArrayList<Group> list = new ArrayList<>();
        for (Group group : geneLabels) {
            if (group != null && getChildren().contains(group))
                list.add(group);
        }
        return list;
    }

    public ReadLayoutPaneSearcher getSearcher() {
        return readLayoutPaneSearcher;
    }

    public int getPreferredHeightUnlabeled() {
        return preferredHeightUnlabeled.get();
    }

    public IntegerProperty preferredHeightUnlabeledProperty() {
        return preferredHeightUnlabeled;
    }

    public void setPreferredHeightUnlabeled(int preferredHeightUnlabeled) {
        this.preferredHeightUnlabeled.set(preferredHeightUnlabeled);
    }

    public int getPreferredHeightLabeled() {
        return preferredHeightLabeled.get();
    }

    public IntegerProperty preferredHeightLabeledProperty() {
        return preferredHeightLabeled;
    }

    public void setPreferredHeightLabeled(int preferredHeightLabeled) {
        this.preferredHeightLabeled.set(preferredHeightLabeled);
    }

    public IntervalTree<IMatchBlock> getIntervals() {
        return intervals;
    }

    public String[] getCNames() {
        return cNames;
    }


    /**
     * creates a label
     *
     * @param text
     * @return label
     */
    private Label createLabel(String text, String fullText, Map<String, ArrayList<Label>> text2oldLabels) {
        if (text2oldLabels.size() > 0) // if there are some old labels, try to reuse one...
        {
            final ArrayList<Label> labels = text2oldLabels.get(text);
            if (labels != null) {
                final Label label = labels.remove(0);
                label.setVisible(true);
                if (labels.size() == 0)
                    text2oldLabels.remove(text);
                return label;
            }
        }
        final Label label = new Label();

        if (text != null)
            label.setText(text);
        label.setFont(font);
        label.setUserData(new IMatchBlock[0]);
        label.setTooltip(new Tooltip(Basic.abbreviateDotDotDot(fullText.replaceAll("\\s+", " "), 100)));
        label.setOnMousePressed(mousePressedHandler);
        label.setOnMouseClicked(mouseClickedHandler);
        label.setOnMouseReleased(mouseReleasedHandler);
        label.setOnMouseDragged(mouseDraggedHandler);
        return label;
    }

    /**
     * get a class name
     *
     * @param classificationId
     * @param classId
     * @return non-null class name
     */
    private String getClassName(int classificationId, int classId) {
        String name = ClassificationManager.get(cNames[classificationId], true).getName2IdMap().get(classId);
        return (name != null ? name : String.format("%s: %d", (cNames[classificationId].equals("INTERPRO2GO") ? "IPR" : cNames[classificationId]), classId));
    }

    /**
     * hide all selected items
     */
    public void hideSelected() {
        for (IMatchBlock matchBlock : matchSelection.getSelectedItems()) {
            final GeneArrow geneArrow = match2GeneArrow.get(matchBlock);
            if (geneArrow != null) {
                geneArrow.setVisible(false);
                for (Label label : geneArrow.getLabels())
                    label.setVisible(false);
                hasHidden = true;
            }
        }
    }

    /**
     * show all items
     */
    public void showAll() {
        hasHidden = false;

        for (IMatchBlock matchBlock : match2GeneArrow.keySet()) {
            final GeneArrow geneArrow = match2GeneArrow.get(matchBlock);
            if (geneArrow != null) {
                geneArrow.setVisible(true);
                for (Label label : geneArrow.getLabels())
                    label.setVisible(true);
            }
        }
    }

    /**
     * select all matches that are not an ancestor of the taxon to which this read is assigned
     *
     * @param classId
     */
    public void selectAllCompatibleTaxa(boolean compatible, String classificationName, int classId) {
        if (classId > 0) {
            if (classificationName.equals(Classification.Taxonomy)) {
                final Classification classification = ClassificationManager.get(Classification.Taxonomy, true);
                for (IMatchBlock matchBlock : match2GeneArrow.keySet()) {
                    int matchClassId = matchBlock.getId(classificationName);
                    boolean isCompatibleWith = (matchClassId > 0 && (matchClassId == classId || classification.getFullTree().isDescendant(classId, matchClassId)));
                    if (compatible == isCompatibleWith)
                        matchSelection.select(matchBlock);
                    else
                        matchSelection.clearSelect(matchBlock);
                }
            } else {
                for (IMatchBlock matchBlock : match2GeneArrow.keySet()) {
                    if (matchBlock.getId(classificationName) == classId)
                        matchSelection.select(matchBlock);
                    else
                        matchSelection.clearSelect(matchBlock);
                }
            }
        }
    }

    public ASelectionModel<IMatchBlock> getMatchSelection() {
        return matchSelection;
    }

    public boolean hasHiddenAlignments() {
        return hasHidden;
    }
}