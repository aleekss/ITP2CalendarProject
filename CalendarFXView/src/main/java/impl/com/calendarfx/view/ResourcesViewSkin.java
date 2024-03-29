/*
 *  Copyright (C) 2017 Dirk Lemmermann Software & Consulting (dlsc.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package impl.com.calendarfx.view;

import com.calendarfx.model.Resource;
import com.calendarfx.util.ViewHelper;
import com.calendarfx.view.AllDayView;
import com.calendarfx.view.ResourcesView;
import com.calendarfx.view.ResourcesView.Type;
import com.calendarfx.view.TimeScaleView;
import com.calendarfx.view.WeekDayHeaderView;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.controlsfx.control.PlusMinusSlider;

import java.time.LocalDate;

public class ResourcesViewSkin<T extends Resource<?>> extends DateControlSkin<ResourcesView<T>> {

    private final GridPane gridPane;
    private final ScrollBar scrollBar;
    private final PlusMinusSlider plusMinusSlider;
    private DayViewScrollPane dayViewScrollPane;
    private final ResourcesViewContainer<T> resourcesViewContainer;
    private final TimeScaleView timeScaleView;

    public ResourcesViewSkin(ResourcesView<T> view) {
        super(view);

        scrollBar = new ScrollBar();
        scrollBar.setOrientation(Orientation.VERTICAL);
        scrollBar.visibleProperty().bind(view.scrollingEnabledProperty().not());

        plusMinusSlider = new PlusMinusSlider();
        plusMinusSlider.setOrientation(Orientation.VERTICAL);
        plusMinusSlider.visibleProperty().bind(view.scrollingEnabledProperty());

        plusMinusSlider.setOnValueChanged(evt -> {
            // exponential function to increase scrolling speed when reaching ends of slider
            final double base = plusMinusSlider.getValue();
            final double pow = Math.signum(plusMinusSlider.getValue()) * Math.pow(base, 2);
            final double pixel = pow * -100;
            view.setScrollTime(view.getZonedDateTimeAt(0, pixel, view.getZoneId()));
        });

        resourcesViewContainer = new ResourcesViewContainer<>(view);
        resourcesViewContainer.setMinHeight(0);
        view.bind(resourcesViewContainer, true);

        timeScaleView = new TimeScaleView();
        view.bind(timeScaleView, true);

        InvalidationListener updateViewListener = it -> updateView();
        view.showAllDayViewProperty().addListener(updateViewListener);
        view.showTimeScaleViewProperty().addListener(updateViewListener);
        view.layoutProperty().addListener(updateViewListener);
        view.showScrollBarProperty().addListener(updateViewListener);
        view.showTimeScaleViewProperty().addListener(updateViewListener);
        view.numberOfDaysProperty().addListener(updateViewListener);
        view.typeProperty().addListener(updateViewListener);
        view.getResources().addListener(updateViewListener);
        view.scrollingEnabledProperty().addListener(updateViewListener);

        RowConstraints row0 = new RowConstraints();
        row0.setFillHeight(true);
        row0.setPrefHeight(Region.USE_COMPUTED_SIZE);
        row0.setVgrow(Priority.NEVER);

        RowConstraints row1 = new RowConstraints();
        row1.setFillHeight(true);
        row1.setPrefHeight(Region.USE_COMPUTED_SIZE);
        row1.setVgrow(Priority.ALWAYS);

        gridPane = new GridPane();
        gridPane.getRowConstraints().setAll(row0, row1);
        gridPane.getStyleClass().add("container");

        getChildren().add(gridPane);

        updateView();

        /*
         * Run later when the control has become visible.
         */
        Platform.runLater(() -> {
            if (dayViewScrollPane != null) {
                ViewHelper.scrollToRequestedTime(view, dayViewScrollPane);
            }
        });

        view.requestedTimeProperty().addListener(it -> {
            if (dayViewScrollPane != null) {
                ViewHelper.scrollToRequestedTime(view, dayViewScrollPane);
            }
        });
    }

    private void updateView() {
        gridPane.getChildren().clear();
        gridPane.getColumnConstraints().clear();

        dayViewScrollPane = null;

        ResourcesView<T> resourcesView = getSkinnable();
        if (resourcesView.getType().equals(Type.RESOURCES_OVER_DATES)) {
            updateViewResourcesOverDates();
        } else {
            updateViewDatesOverResources();
        }
    }

    private void updateViewDatesOverResources() {
        final ResourcesView<T> resourcesView = getSkinnable();

        if (resourcesView.isShowTimeScaleView()) {
            ColumnConstraints timeScaleColumn = new ColumnConstraints();
            timeScaleColumn.setFillWidth(true);
            timeScaleColumn.setHgrow(Priority.NEVER);
            gridPane.getColumnConstraints().add(timeScaleColumn);

            if (resourcesView.isScrollingEnabled()) {
                gridPane.add(timeScaleView, 0, 1);
            } else {
                DayViewScrollPane timeScaleScrollPane = new DayViewScrollPane(timeScaleView, scrollBar);
                timeScaleScrollPane.getStyleClass().addAll("calendar-scroll-pane", "day-view-timescale-scroll-pane");
                timeScaleScrollPane.setMinWidth(Region.USE_PREF_SIZE);
                gridPane.add(timeScaleScrollPane, 0, 1);
            }

            Node upperLeftCorner = resourcesView.getUpperLeftCorner();
            upperLeftCorner.getStyleClass().add("upper-left-corner");
            gridPane.add(upperLeftCorner, 0, 0);
        }

        if (resourcesView.isShowScrollBar()) {
            Node upperRightCorner = resourcesView.getUpperRightCorner();
            upperRightCorner.getStyleClass().add("upper-right-corner");
            gridPane.add(upperRightCorner, 2, 0);
        }

        Callback<T, Node> resourceHeaderFactory = resourcesView.getResourceHeaderFactory();

        ObservableList<T> resources = resourcesView.getResources();

        HBox headerBox = new HBox();
        headerBox.getStyleClass().add("header-box");

        gridPane.add(headerBox, 1, 0);

        for (int dayIndex = 0; dayIndex < resourcesView.getNumberOfDays(); dayIndex++) {
            ObjectProperty<LocalDate> dateProperty = new SimpleObjectProperty<>(this, "date");
            final int additionalDays = dayIndex;
            dateProperty.bind(Bindings.createObjectBinding(() -> resourcesView.getDate().plusDays(additionalDays), resourcesView.dateProperty()));

            VBox dayBox = new VBox();
            dayBox.getStyleClass().add("day-box");
            HBox.setHgrow(dayBox, Priority.ALWAYS);

            WeekDayHeaderView weekDayHeaderView = new WeekDayHeaderView(resourcesView.getNumberOfDays());
            resourcesView.bind(weekDayHeaderView, false);

            weekDayHeaderView.dateProperty().bind(dateProperty);
            weekDayHeaderView.setNumberOfDays(1);
            weekDayHeaderView.setAdjustToFirstDayOfWeek(false);

            weekDayHeaderView.getStyleClass().removeAll("only", "first", "middle", "last");

            if (resourcesView.getNumberOfDays() == 1) {
                weekDayHeaderView.getStyleClass().add("only");
            } else {
                if (dayIndex == 0) {
                    weekDayHeaderView.getStyleClass().add("first");
                } else if (dayIndex == resourcesView.getNumberOfDays() - 1) {
                    weekDayHeaderView.getStyleClass().add("last");
                } else {
                    weekDayHeaderView.getStyleClass().add("middle");
                }
            }

            dayBox.getChildren().add(weekDayHeaderView);

            HBox allResourcesBox = new HBox();
            VBox.setVgrow(allResourcesBox, Priority.ALWAYS);

            dayBox.getChildren().add(allResourcesBox);

            headerBox.getChildren().add(dayBox);

            // separator between dates
            if (dayIndex < resourcesView.getNumberOfDays() - 1) {
                Callback<ResourcesView<T>, Region> separatorFactory = resourcesView.getLargeSeparatorFactory();
                if (separatorFactory != null) {
                    Region separator = separatorFactory.call(resourcesView);
                    if (separator != null) {
                        headerBox.getChildren().add(separator);
                        HBox.setHgrow(separator, Priority.NEVER);
                    }
                }
            }

            for (int resourceIndex = 0; resourceIndex < resources.size(); resourceIndex++) {
                T resource = resources.get(resourceIndex);

                Node resourceHeaderNode = resourceHeaderFactory.call(resource);

                VBox singleResourceBox = new VBox(resourceHeaderNode);
                HBox.setHgrow(singleResourceBox, Priority.ALWAYS);

                allResourcesBox.getChildren().add(singleResourceBox);

                resourceHeaderNode.getStyleClass().removeAll("only", "first", "middle", "last");

                if (resources.size() == 1) {
                    resourceHeaderNode.getStyleClass().add("only");
                } else {
                    if (resourceIndex == 0) {
                        resourceHeaderNode.getStyleClass().add("first");
                    } else if (resourceIndex == resources.size() - 1) {
                        resourceHeaderNode.getStyleClass().add("last");
                    } else {
                        resourceHeaderNode.getStyleClass().add("middle");
                    }
                }

                if (resourcesView.isShowAllDayView()) {

                    Callback<T, AllDayView> allDayViewFactory = resourcesView.getAllDayViewFactory();
                    AllDayView allDayView = allDayViewFactory.call(resource);

                    allDayView.getStyleClass().removeAll("only", "first", "middle", "last");

                    if (resources.size() == 1) {
                        allDayView.getStyleClass().add("only");
                    } else {
                        if (resourceIndex == 0) {
                            allDayView.getStyleClass().add("first");
                        } else if (resourceIndex == resources.size() - 1) {
                            allDayView.getStyleClass().add("last");
                        } else {
                            allDayView.getStyleClass().add("middle");
                        }
                    }

                    // bind AllDayView
                    resourcesView.bind(allDayView, false);

                    Bindings.unbindBidirectional(resourcesView.adjustToFirstDayOfWeekProperty(), allDayView.adjustToFirstDayOfWeekProperty());
                    Bindings.unbindBidirectional(resourcesView.numberOfDaysProperty(), allDayView.numberOfDaysProperty());

                    allDayView.dateProperty().bind(dateProperty);
                    allDayView.setAdjustToFirstDayOfWeek(false);
                    allDayView.setNumberOfDays(1);

                    // some un-bindings for AllDayView
                    Bindings.unbindBidirectional(resourcesView.defaultCalendarProviderProperty(), allDayView.defaultCalendarProviderProperty());
                    Bindings.unbindBidirectional(resourcesView.draggedEntryProperty(), allDayView.draggedEntryProperty());
                    Bindings.unbindContentBidirectional(resourcesView.getCalendarSources(), allDayView.getCalendarSources());

                    Bindings.bindContent(allDayView.getCalendarSources(), resource.getCalendarSources());

                    VBox.setVgrow(allDayView, Priority.ALWAYS);
                    singleResourceBox.getChildren().add(allDayView);
                }

                singleResourceBox.setPrefWidth(0); // so they all end up with the same percentage width

                HBox.setHgrow(singleResourceBox, Priority.ALWAYS);

                if (resourceIndex < resources.size() - 1) {
                    Callback<ResourcesView<T>, Region> separatorFactory = resourcesView.getSmallSeparatorFactory();
                    if (separatorFactory != null) {
                        Region separator = separatorFactory.call(resourcesView);
                        if (separator != null) {
                            allResourcesBox.getChildren().add(separator);
                            HBox.setHgrow(separator, Priority.NEVER);
                        }
                    }
                }
            }
        }

        ColumnConstraints containerOrScrollPaneConstraints = new ColumnConstraints();
        containerOrScrollPaneConstraints.setFillWidth(true);
        containerOrScrollPaneConstraints.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().add(containerOrScrollPaneConstraints);

        if (resourcesView.isScrollingEnabled()) {
            gridPane.add(resourcesViewContainer, 1, 1);
        } else {
            dayViewScrollPane = new DayViewScrollPane(resourcesViewContainer, scrollBar);
            gridPane.add(dayViewScrollPane, 1, 1);
        }

        if (resourcesView.isShowScrollBar()) {
            ColumnConstraints scrollbarConstraint = new ColumnConstraints();
            scrollbarConstraint.setFillWidth(true);
            scrollbarConstraint.setHgrow(Priority.NEVER);
            scrollbarConstraint.setPrefWidth(Region.USE_COMPUTED_SIZE);

            gridPane.getColumnConstraints().add(scrollbarConstraint);

            if (resourcesView.isScrollingEnabled()) {
                gridPane.add(plusMinusSlider, 2, 1);
            } else {
                gridPane.add(scrollBar, 2, 1);
            }
        }
    }

    private void updateViewResourcesOverDates() {
        final ResourcesView<T> resourcesView = getSkinnable();

        if (resourcesView.isShowTimeScaleView()) {
            ColumnConstraints timeScaleColumn = new ColumnConstraints();
            timeScaleColumn.setFillWidth(true);
            timeScaleColumn.setHgrow(Priority.NEVER);
            gridPane.getColumnConstraints().add(timeScaleColumn);

            if (resourcesView.isScrollingEnabled()) {
                gridPane.add(timeScaleView, 0, 1);
            } else {
                // time scale scroll pane
                DayViewScrollPane timeScaleScrollPane = new DayViewScrollPane(timeScaleView, scrollBar);
                timeScaleScrollPane.getStyleClass().addAll("calendar-scroll-pane", "day-view-timescale-scroll-pane");
                timeScaleScrollPane.setMinWidth(Region.USE_PREF_SIZE);
                gridPane.add(timeScaleScrollPane, 0, 1);
            }

            Node upperLeftCorner = resourcesView.getUpperLeftCorner();
            upperLeftCorner.getStyleClass().add("upper-left-corner");
            gridPane.add(upperLeftCorner, 0, 0);
        }

        if (resourcesView.isShowScrollBar()) {
            Node upperRightCorner = resourcesView.getUpperRightCorner();
            upperRightCorner.getStyleClass().add("upper-right-corner");
            gridPane.add(upperRightCorner, 2, 0);
        }

        HBox headerBox = new HBox();
        headerBox.getStyleClass().add("header-box");

        gridPane.add(headerBox, 1, 0);

        Callback<T, Node> resourceHeaderFactory = resourcesView.getResourceHeaderFactory();

        ObservableList<T> resources = resourcesView.getResources();
        for (int i = 0; i < resources.size(); i++) {
            T resource = resources.get(i);

            Node headerNode = resourceHeaderFactory.call(resource);

            VBox resourceHeader = new VBox(headerNode);
            resourceHeader.getStyleClass().removeAll("only", "first", "middle", "last");

            if (resources.size() == 1) {
                resourceHeader.getStyleClass().add("only");
            } else {
                if (i == 0) {
                    resourceHeader.getStyleClass().add("first");
                } else if (i == resources.size() - 1) {
                    resourceHeader.getStyleClass().add("last");
                } else {
                    resourceHeader.getStyleClass().add("middle");
                }
            }

            if (resourcesView.isShowAllDayView()) {
                AllDayView allDayView = new AllDayView();
                allDayView.setAdjustToFirstDayOfWeek(false);

                // bind AllDayView
                resourcesView.bind(allDayView, true);
                allDayView.numberOfDaysProperty().bind(resourcesView.numberOfDaysProperty());

                // rebind
                allDayView.adjustToFirstDayOfWeekProperty().bind(resourcesView.adjustToFirstDayOfWeekProperty());

                // some unbindings for AllDayView
                Bindings.unbindBidirectional(resourcesView.defaultCalendarProviderProperty(), allDayView.defaultCalendarProviderProperty());
                Bindings.unbindBidirectional(resourcesView.draggedEntryProperty(), allDayView.draggedEntryProperty());
                Bindings.unbindContentBidirectional(resourcesView.getCalendarSources(), allDayView.getCalendarSources());

                Bindings.bindContent(allDayView.getCalendarSources(), resource.getCalendarSources());
                resourceHeader.getChildren().add(allDayView);
            }

            resourceHeader.getStyleClass().add("resource-header-view");

            WeekDayHeaderView weekDayHeaderView = resourcesView.getWeekDayHeaderViewFactory().call(resource);
            weekDayHeaderView.adjustToFirstDayOfWeekProperty().bind(resourcesView.adjustToFirstDayOfWeekProperty());
            weekDayHeaderView.numberOfDaysProperty().bind(resourcesView.numberOfDaysProperty());
            resourcesView.bind(weekDayHeaderView, true);

            resourceHeader.setPrefWidth(0); // so they all end up with the same percentage width
            resourceHeader.getChildren().add(weekDayHeaderView);

            headerBox.getChildren().add(resourceHeader);
            HBox.setHgrow(resourceHeader, Priority.ALWAYS);

            if (i < resources.size() - 1) {
                Callback<ResourcesView<T>, Region> separatorFactory = resourcesView.getLargeSeparatorFactory();
                if (separatorFactory != null) {
                    Region separator = separatorFactory.call(resourcesView);
                    if (separator != null) {
                        headerBox.getChildren().add(separator);
                        HBox.setHgrow(separator, Priority.NEVER);
                    }
                }
            }
        }

        ColumnConstraints dayViewsConstraints = new ColumnConstraints();
        dayViewsConstraints.setFillWidth(true);
        dayViewsConstraints.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().add(dayViewsConstraints);

        if (resourcesView.isScrollingEnabled()) {
            resourcesViewContainer.setTranslateY(0);
            resourcesViewContainer.setManaged(true);
            gridPane.add(resourcesViewContainer, 1, 1);
        } else {
            dayViewScrollPane = new DayViewScrollPane(resourcesViewContainer, scrollBar);
            gridPane.add(dayViewScrollPane, 1, 1);
        }

        if (resourcesView.isShowScrollBar()) {
            ColumnConstraints scrollbarConstraint = new ColumnConstraints();
            scrollbarConstraint.setFillWidth(true);
            scrollbarConstraint.setHgrow(Priority.NEVER);
            scrollbarConstraint.setPrefWidth(Region.USE_COMPUTED_SIZE);
            gridPane.getColumnConstraints().add(scrollbarConstraint);

            gridPane.add(scrollBar, 2, 1);
        }
    }
}
