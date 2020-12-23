/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.visualization.featurelisttable_modular;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.util.ExitCode;
import io.github.mzmine.util.RangeUtils;
import io.github.mzmine.util.javafx.FxIconUtil;
import java.text.NumberFormat;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

public class FeatureTableFXMLTabAnchorPaneController {

  private static final Logger logger = Logger
      .getLogger(FeatureTableFXMLTabAnchorPaneController.class.getName());

  private static ParameterSet param;

  @FXML
  private SplitPane pnFilters;
  @FXML
  private SplitPane pnTablePreviewSplit;
  @FXML
  private FeatureTableFX featureTable;

  private TextField mzSearchField;
  private TextField rtSearchField;

  public void initialize() {
    param = MZmineCore.getConfiguration()
        .getModuleParameters(FeatureTableFXModule.class);

    // Filters hbox
    HBox filtersRow = new HBox();
    filtersRow.setAlignment(Pos.CENTER_LEFT);
    filtersRow.setSpacing(10.0);
    Separator separator = new Separator(Orientation.VERTICAL);

    // Filter icon
    ImageView filterIcon
        = new ImageView(FxIconUtil.loadImageFromResources("icons/filtericon.png"));

    // Search fields
    mzSearchField = new TextField();
    //mzSearchField.setStyle("-fx-prompt-text-fill: derive(-fx-control-inner-background, -30%);");
    rtSearchField = new TextField();
    // Add filter text fields listeners to filter on air
    mzSearchField.textProperty().addListener((observable, oldValue, newValue) -> filterRows());
    rtSearchField.textProperty().addListener((observable, oldValue, newValue) -> filterRows());
    HBox mzFilter = new HBox(new Text("m/z: "), mzSearchField);
    mzFilter.setAlignment(filtersRow.getAlignment());
    HBox rtFilter = new HBox(new Text("RT: "), rtSearchField);
    rtFilter.setAlignment(filtersRow.getAlignment());

    filtersRow.getChildren().addAll(filterIcon, mzFilter, separator, rtFilter);

    pnFilters.getItems().add(filtersRow);

    featureTable.getSelectionModel().selectedItemProperty()
        .addListener(((obs, o, n) -> selectedRowChanged()));
  }

  private void filterRows() {
    // Parse input text fields
    Range<Double> mzFilter = parseNumericFilter(mzSearchField, 5e-5);
    Range<Double> rtFilter = parseNumericFilter(rtSearchField, 5e-3);

    // Filter strings are invalid, do nothing
    if (RangeUtils.isNaNRange(mzFilter) || RangeUtils.isNaNRange(rtFilter)) {
      return;
    }

    // Filter rows
    featureTable.getFilteredRowItems().setPredicate(item -> {
      FeatureListRow row = item.getValue();
      return mzFilter.contains(row.getAverageMZ()) && rtFilter.contains((double) row.getAverageRT());
    });

    // Update rows in feature table
    featureTable.getRoot().getChildren().clear();
    featureTable.getRoot().getChildren().addAll(featureTable.getFilteredRowItems());
  }

  /**
   * Parses string of the given filter text field and returns a range of values satisfying the filter.
   * Examples:
   *  "5.34" -> [5.34 - epsilon, 5.34 + epsilon]
   *  "2.37 - 6" -> [2.37 - epsilon, 6.00 + epsilon]
   *
   * @param textField Text field
   * @param epsilon Precision of the filter
   * @return Range of values satisfying the filter or RangeUtils.DOUBLE_NAN_RANGE if the filter
   * string is invalid
   */
  private Range<Double> parseNumericFilter(TextField textField, double epsilon) {
    textField.setStyle("-fx-control-inner-background: #ffffff;");
    String filterStr = textField.getText();
    filterStr = filterStr.replace(" ","");

    if (filterStr.isEmpty()) { // Empty filter
      textField.setStyle("-fx-prompt-text-fill: derive(-fx-control-inner-background, -30%);");
      return RangeUtils.DOUBLE_INFINITE_RANGE;
    } else if (filterStr.contains("-")) { // Filter by range
      try {
        Range<Double> parsedRange = RangeUtils.parseDoubleRange(filterStr);
        return Range.closed(parsedRange.lowerEndpoint() - epsilon, parsedRange.upperEndpoint() + epsilon);
      } catch (Exception exception) {
        textField.setStyle("-fx-control-inner-background: #ffcccb;");
        return RangeUtils.DOUBLE_NAN_RANGE;
      }
    } else { // Filter by single value
      try {
        double filterValue = Double.parseDouble(filterStr);
        return Range.closed(filterValue - epsilon, filterValue + epsilon);
      } catch (Exception exception) {
        textField.setStyle("-fx-control-inner-background: #ffcccb;");
        return RangeUtils.DOUBLE_NAN_RANGE;
      }
    }
  }

  @FXML
  public void miParametersOnAction(ActionEvent event) {
    Platform.runLater(() -> {
      ExitCode exitCode = param.showSetupDialog(true);
      if (exitCode == ExitCode.OK) {
        updateWindowToParameterSetValues();
      }
    });
  }

  /**
   * In case the parameters are changed in the setup dialog, they are applied to the window.
   */
  void updateWindowToParameterSetValues() {
    featureTable.applyColumnsVisibility(
        param.getParameter(FeatureTableFXParameters.showRowTypeColumns).getValue(),
        param.getParameter(FeatureTableFXParameters.showFeatureTypeColumns).getValue());
  }

  public void setFeatureList(FeatureList featureList) {
    featureTable.addData(featureList);

    if(featureList==null) {
      return;
    }

    // Fill filters text fields with a prompt values
    NumberFormat mzFormat = MZmineCore.getConfiguration().getMZFormat();
    Range<Double> mzRange = featureList.getRowsMZRange();
    if(mzRange!=null)
      mzSearchField.setPromptText(mzFormat.format(mzRange.lowerEndpoint()) + " - "
        + mzFormat.format(mzRange.upperEndpoint()));
    mzSearchField.setStyle("-fx-prompt-text-fill: derive(-fx-control-inner-background, -30%);");

    NumberFormat rtFormat = MZmineCore.getConfiguration().getRTFormat();
    Range<Float> rtRange = featureTable.getFeatureList().getRowsRTRange();
    if(rtRange!=null)
      rtSearchField.setPromptText(rtFormat.format(rtRange.lowerEndpoint()) + " - "
        + rtFormat.format(rtRange.upperEndpoint()));
    rtSearchField.setStyle("-fx-prompt-text-fill: derive(-fx-control-inner-background, -30%);");
  }

  void selectedRowChanged() {
    TreeItem<FeatureListRow> selectedItem = featureTable.getSelectionModel()
        .getSelectedItem();
//    featureTable.getColumns().forEach(c -> logger.info(c.getText()));
    logger.info(
        "selected: " + featureTable.getSelectionModel().getSelectedCells().get(0).getTableColumn()
            .getText());

    if (selectedItem == null) {
      return;
    }

    FeatureListRow selectedRow = selectedItem.getValue();
    if (selectedRow == null) {
      return;
    }
  }
}
