/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener;
import com.intellij.execution.testframework.sm.runner.SMTRunnerTreeBuilder;
import com.intellij.execution.testframework.sm.runner.SMTRunnerTreeStructure;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.statistics.StatisticsPanel;
import com.intellij.execution.testframework.ui.AbstractTestTreeBuilder;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author: Roman Chernyatchik
 */
public class SMTestRunnerResultsForm extends TestResultsPanel
  implements TestFrameworkRunningModel, TestResultsViewer, SMTRunnerEventsListener {
  @NonNls private static final String DEFAULT_SM_RUNNER_SPLITTER_PROPERTY = "SMTestRunner.Splitter.Proportion";

  public static final Color DARK_YELLOW = JBColor.YELLOW.darker();

  private SMTRunnerTestTreeView myTreeView;

  private TestsProgressAnimator myAnimator;

  /**
   * Fake parent suite for all tests and suites
   */
  private final SMTestProxy.SMRootTestProxy myTestsRootNode;
  private SMTRunnerTreeBuilder myTreeBuilder;
  private final TestConsoleProperties myConsoleProperties;

  private final List<EventsListener> myEventListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private PropagateSelectionHandler myShowStatisticForProxyHandler;

  private final Project myProject;

  private int myTestsCurrentCount;
  private int myTestsTotal = 0;
  private int myTestsFailuresCount;
  private boolean myContainsIgnoredTests;
  private long myStartTime;
  private long myEndTime;
  private StatisticsPanel myStatisticsPane;

  // custom progress
  private String myCurrentCustomProgressCategory;
  private final Set<String> myMentionedCategories = new LinkedHashSet<String>();

  public SMTestRunnerResultsForm(final RunConfiguration runConfiguration,
                                 @NotNull final JComponent console,
                                 final TestConsoleProperties consoleProperties,
                                 final ExecutionEnvironment environment) {
    this(runConfiguration, console, AnAction.EMPTY_ARRAY, consoleProperties, environment, null);
  }

  public SMTestRunnerResultsForm(final RunConfiguration runConfiguration,
                                 @NotNull final JComponent console,
                                 AnAction[] consoleActions,
                                 final TestConsoleProperties consoleProperties,
                                 final ExecutionEnvironment environment,
                                 final String splitterPropertyName) {
    super(console, consoleActions, consoleProperties, environment,
          splitterPropertyName != null ? DEFAULT_SM_RUNNER_SPLITTER_PROPERTY : splitterPropertyName, 0.5f);
    myConsoleProperties = consoleProperties;

    myProject = runConfiguration.getProject();

    //Create tests common suite root
    //noinspection HardCodedStringLiteral
    myTestsRootNode = new SMTestProxy.SMRootTestProxy();
    //todo myTestsRootNode.setOutputFilePath(runConfiguration.getOutputFilePath());

    // Fire selection changed and move focus on SHIFT+ENTER
    //TODO[romeo] improve
    /*
    final ArrayList<Component> components = new ArrayList<Component>();
    components.add(myTreeView);
    components.add(myTabs.getComponent());
    myContentPane.setFocusTraversalPolicy(new MyFocusTraversalPolicy(components));
    myContentPane.setFocusCycleRoot(true);
    */
  }

  @Override
  public void initUI() {
    super.initUI();

    final KeyStroke shiftEnterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK);
    SMRunnerUtil.registerAsAction(shiftEnterKey, "show-statistics-for-test-proxy",
                                  new Runnable() {
                                    @Override
                                    public void run() {
                                      showStatisticsForSelectedProxy();
                                    }
                                  },
                                  myTreeView);
  }

  @Override
  protected ToolbarPanel createToolbarPanel() {
    return new SMTRunnerToolbarPanel(myConsoleProperties, myEnvironment, this, this);
  }

  @Override
  protected JComponent createTestTreeView() {
    myTreeView = new SMTRunnerTestTreeView();

    myTreeView.setLargeModel(true);
    myTreeView.attachToModel(this);
    myTreeView.setTestResultsViewer(this);
    addTestsTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        AbstractTestProxy selectedTest = getTreeView().getSelectedTest();
        if (selectedTest instanceof SMTestProxy) {
          myStatisticsPane.selectProxy(((SMTestProxy)selectedTest), this, false);
        }
      }
    });

    final SMTRunnerTreeStructure structure = new SMTRunnerTreeStructure(myProject, myTestsRootNode);
    myTreeBuilder = new SMTRunnerTreeBuilder(myTreeView, structure);
    myTreeBuilder.setTestsComparator(TestConsoleProperties.SORT_ALPHABETICALLY.value(myProperties));
    Disposer.register(this, myTreeBuilder);

    myAnimator = new MyAnimator(this, myTreeBuilder);

    //TODO always hide root node
    //myTreeView.setRootVisible(false);

    return myTreeView;
  }

  @Override
  protected JComponent createStatisticsPanel() {
    // Statistics tab
    final StatisticsPanel statisticsPane = new StatisticsPanel(myProject, this);
    // handler to select in results viewer by statistics pane events
    statisticsPane.addPropagateSelectionListener(createSelectMeListener());
    // handler to select test statistics pane by result viewer events
    setShowStatisticForProxyHandler(statisticsPane.createSelectMeListener());

    myStatisticsPane = statisticsPane;
    return myStatisticsPane.getContentPane();
  }

  public StatisticsPanel getStatisticsPane() {
    return myStatisticsPane;
  }

  public void addTestsTreeSelectionListener(final TreeSelectionListener listener) {
    myTreeView.getSelectionModel().addTreeSelectionListener(listener);
  }

  /**
   * Is used for navigation from tree view to other UI components
   *
   * @param handler
   */
  @Override
  public void setShowStatisticForProxyHandler(final PropagateSelectionHandler handler) {
    myShowStatisticForProxyHandler = handler;
  }

  /**
   * Returns root node, fake parent suite for all tests and suites
   *
   * @param testsRoot
   * @return
   */
  @Override
  public void onTestingStarted(@NotNull SMTestProxy.SMRootTestProxy testsRoot) {
    myAnimator.setCurrentTestCase(myTestsRootNode);

    // Status line
    myStatusLine.setStatusColor(ColorProgressBar.GREEN);

    // Tests tree
    selectAndNotify(myTestsRootNode);

    myStartTime = System.currentTimeMillis();
    myTestsRootNode.addSystemOutput("Testing started at "
                                    + DateFormatUtil.formatTime(myStartTime)
                                    + " ...\n");

    updateStatusLabel(false);

    // TODO : show info - "Loading..." msg

    fireOnTestingStarted();
  }

  @Override
  public void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot) {
    myEndTime = System.currentTimeMillis();

    if (myTestsTotal == 0) {
      myTestsTotal = myTestsCurrentCount;
      myStatusLine.setFraction(1);
    }

    updateStatusLabel(true);
    updateIconProgress();

    myAnimator.stopMovie();
    myTreeBuilder.updateFromRoot();

    LvcsHelper.addLabel(this);

    SMTestProxy root = getTestsRootNode();
    if (root != null) {
      selectAndNotify(root);
    }

    fireOnTestingFinished();
  }

  @Override
  public void onTestsCountInSuite(final int count) {
    updateCountersAndProgressOnTestCount(count, false);
  }

  /**
   * Adds test to tree and updates status line.
   * Test proxy should be initialized, proxy parent must be some suite (already added to tree)
   *
   * @param testProxy Proxy
   */
  @Override
  public void onTestStarted(@NotNull final SMTestProxy testProxy) {
    updateCountersAndProgressOnTestStarted(false);

    _addTestOrSuite(testProxy);

    fireOnTestNodeAdded(testProxy);
  }

  @Override
  public void onTestFailed(@NotNull final SMTestProxy test) {
    updateCountersAndProgressOnTestFailed(false);
    updateIconProgress();
  }

  @Override
  public void onTestIgnored(@NotNull final SMTestProxy test) {
    updateOnTestIgnored();
  }

  /**
   * Adds suite to tree
   * Suite proxy should be initialized, proxy parent must be some suite (already added to tree)
   * If parent is null, then suite will be added to tests root.
   *
   * @param newSuite Tests suite
   */
  @Override
  public void onSuiteStarted(@NotNull final SMTestProxy newSuite) {
    _addTestOrSuite(newSuite);
  }

  @Override
  public void onCustomProgressTestsCategory(@Nullable String categoryName, int testCount) {
    myCurrentCustomProgressCategory = categoryName;
    updateCountersAndProgressOnTestCount(testCount, true);
  }

  @Override
  public void onCustomProgressTestStarted() {
    updateCountersAndProgressOnTestStarted(true);
  }

  @Override
  public void onCustomProgressTestFailed() {
    updateCountersAndProgressOnTestFailed(true);
  }

  @Override
  public void onTestFinished(@NotNull final SMTestProxy test) {
    //Do nothing
    updateIconProgress();
  }

  @Override
  public void onSuiteFinished(@NotNull final SMTestProxy suite) {
    //Do nothing
  }

  @Override
  public SMTestProxy.SMRootTestProxy getTestsRootNode() {
    return myTestsRootNode;
  }

  @Override
  public TestConsoleProperties getProperties() {
    return myConsoleProperties;
  }

  @Override
  public void setFilter(final Filter filter) {
    // is used by Test Runner actions, e.g. hide passed, etc
    final SMTRunnerTreeStructure treeStructure = myTreeBuilder.getSMRunnerTreeStructure();
    treeStructure.setFilter(filter);

    // TODO - show correct info if no children are available
    // (e.g no tests found or all tests passed, etc.)
    // treeStructure.getChildElements(treeStructure.getRootElement()).length == 0

    myTreeBuilder.queueUpdate();
  }

  @Override
  public boolean isRunning() {
    return getRoot().isInProgress();
  }

  @Override
  public TestTreeView getTreeView() {
    return myTreeView;
  }

  @Override
  public SMTRunnerTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  @Override
  public boolean hasTestSuites() {
    return getRoot().getChildren().size() > 0;
  }

  @Override
  @NotNull
  public AbstractTestProxy getRoot() {
    return myTestsRootNode;
  }

  /**
   * Manual test proxy selection in tests tree. E.g. do select root node on
   * testing started or do select current node if TRACK_RUNNING_TEST is enabled
   * <p/>
   * <p/>
   * Will select proxy in Event Dispatch Thread. Invocation of this
   * method may be not in event dispatch thread
   *
   * @param testProxy Test or suite
   */
  @Override
  public void selectAndNotify(@Nullable final AbstractTestProxy testProxy) {
    selectWithoutNotify(testProxy);

    // Is used by Statistic tab to differ use selection in tree
    // from manual selection from API (e.g. test runner events)
    showStatisticsForSelectedProxy(testProxy, false);
  }

  @Override
  public void addEventsListener(final EventsListener listener) {
    myEventListeners.add(listener);
    addTestsTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(final TreeSelectionEvent e) {
        //We should fire event only if it was generated by this component,
        //e.g. it is focused. Otherwise it is side effect of selecting proxy in
        //try by other component
        //if (myTreeView.isFocusOwner()) {
        @Nullable final SMTestProxy selectedProxy = (SMTestProxy)getTreeView().getSelectedTest();
        listener.onSelected(selectedProxy, SMTestRunnerResultsForm.this, SMTestRunnerResultsForm.this);
        //}
      }
    });
  }

  @Override
  public void dispose() {
    super.dispose();
    myShowStatisticForProxyHandler = null;
    myEventListeners.clear();
    myStatisticsPane.doDispose();
  }

  @Override
  public void showStatisticsForSelectedProxy() {
    TestConsoleProperties.SHOW_STATISTICS.set(myProperties, true);
    final AbstractTestProxy selectedProxy = myTreeView.getSelectedTest();
    showStatisticsForSelectedProxy(selectedProxy, true);
  }

  private void showStatisticsForSelectedProxy(final AbstractTestProxy selectedProxy,
                                              final boolean requestFocus) {
    if (selectedProxy instanceof SMTestProxy && myShowStatisticForProxyHandler != null) {
      myShowStatisticForProxyHandler.handlePropagateSelectionRequest((SMTestProxy)selectedProxy, this, requestFocus);
    }
  }

  protected int getTestsCurrentCount() {
    return myTestsCurrentCount;
  }

  protected int getTestsFailuresCount() {
    return myTestsFailuresCount;
  }

  protected Color getTestsStatusColor() {
    return myStatusLine.getStatusColor();
  }

  protected int getTestsTotal() {
    return myTestsTotal;
  }

  public Set<String> getMentionedCategories() {
    return myMentionedCategories;
  }

  protected long getStartTime() {
    return myStartTime;
  }

  protected long getEndTime() {
    return myEndTime;
  }

  private void _addTestOrSuite(@NotNull final SMTestProxy newTestOrSuite) {

    final SMTestProxy parentSuite = newTestOrSuite.getParent();
    assert parentSuite != null;

    // Tree
    myTreeBuilder.updateTestsSubtree(parentSuite);
    myTreeBuilder.repaintWithParents(newTestOrSuite);

    myAnimator.setCurrentTestCase(newTestOrSuite);
  }

  private void fireOnTestNodeAdded(final SMTestProxy test) {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestNodeAdded(this, test);
    }
  }

  private void fireOnTestingFinished() {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestingFinished(this);
    }
  }

  private void fireOnTestingStarted() {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestingStarted(this);
    }
  }

  private void selectWithoutNotify(final AbstractTestProxy testProxy) {
    if (testProxy == null) {
      return;
    }

    SMRunnerUtil.runInEventDispatchThread(new Runnable() {
      @Override
      public void run() {
        if (myTreeBuilder.isDisposed()) {
          return;
        }
        myTreeBuilder.select(testProxy, null);
      }
    }, ModalityState.NON_MODAL);
  }

  private void updateStatusLabel(final boolean testingFinished) {
    if (myTestsFailuresCount > 0) {
      myStatusLine.setStatusColor(ColorProgressBar.RED);
    }
    else if (myContainsIgnoredTests) {
      myStatusLine.setStatusColor(DARK_YELLOW);
    }

    if (testingFinished) {
      if (myTestsTotal == 0) {
        myStatusLine.setStatusColor(myTestsRootNode.wasLaunched() || !myTestsRootNode.isTestsReporterAttached()
                                    ? JBColor.LIGHT_GRAY
                                    : ColorProgressBar.RED);
      }
      // else color will be according failed/passed tests
    }

    // launchedAndFinished - is launched and not in progress. If we remove "launched' that onTestingStarted() before
    // initializing will be "launchedAndFinished"
    final boolean launchedAndFinished = myTestsRootNode.wasLaunched() && !myTestsRootNode.isInProgress();
    myStatusLine.setText(TestsPresentationUtil.getProgressStatus_Text(myStartTime, myEndTime,
                                                                      myTestsTotal, myTestsCurrentCount,
                                                                      myTestsFailuresCount, myMentionedCategories,
                                                                      launchedAndFinished));
  }

  /**
   * for java unit tests
   */
  public void performUpdate() {
    myTreeBuilder.performUpdate();
  }

  private void updateIconProgress() {
    final boolean isIndeterminate = myTestsTotal == 0;
    final int total = isIndeterminate ? 2 : myTestsTotal;
    final int count = isIndeterminate ? 1 : myTestsCurrentCount;
    TestsUIUtil.showIconProgress(myProject, count, total, myTestsFailuresCount);
  }

  /**
   * On event change selection and probably requests focus. Is used when we want
   * navigate from other component to this
   *
   * @return Listener
   */
  public PropagateSelectionHandler createSelectMeListener() {
    return new PropagateSelectionHandler() {
      @Override
      public void handlePropagateSelectionRequest(@Nullable final SMTestProxy selectedTestProxy, @NotNull final Object sender,
                                                  final boolean requestFocus) {
        SMRunnerUtil.addToInvokeLater(new Runnable() {
          @Override
          public void run() {
            selectWithoutNotify(selectedTestProxy);

            // Request focus if necessary
            if (requestFocus) {
              //myTreeView.requestFocusInWindow();
              IdeFocusManager.getInstance(myProject).requestFocus(myTreeView, true);
            }
          }
        });
      }
    };
  }


  private static class MyAnimator extends TestsProgressAnimator {
    public MyAnimator(final Disposable parentDisposable, final AbstractTestTreeBuilder builder) {
      super(parentDisposable);
      init(builder);
    }
  }

  private void updateCountersAndProgressOnTestCount(final int count, final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;

    //This is for better support groups of TestSuites
    //Each group notifies about it's size
    myTestsTotal += count;
    updateStatusLabel(false);
  }

  private void updateCountersAndProgressOnTestStarted(final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;

    // for mixed tests results : mention category only if it contained tests
    myMentionedCategories
      .add(myCurrentCustomProgressCategory != null ? myCurrentCustomProgressCategory : TestsPresentationUtil.DEFAULT_TESTS_CATEGORY);

    // Counters
    myTestsCurrentCount++;

    // fix total count if it is corrupted
    // but if test count wasn't set at all let's process such case separately
    if (myTestsCurrentCount > myTestsTotal && myTestsTotal != 0) {
      myTestsTotal = myTestsCurrentCount;
    }

    // update progress
    if (myTestsTotal != 0) {
      // if total is set
      myStatusLine.setFraction((double)myTestsCurrentCount / myTestsTotal);
    }
    else {
      // if at least one test was launcher than just set progress in the middle to show user that tests are running
      myStatusLine.setFraction(myTestsCurrentCount > 1 ? 0.5 : 0); // > 1 because count already ++
    }
    updateStatusLabel(false);
  }

  private void updateCountersAndProgressOnTestFailed(final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;

    myTestsFailuresCount++;
    updateStatusLabel(false);
  }

  private void updateOnTestIgnored() {
    myContainsIgnoredTests = true;
    updateStatusLabel(false);
  }

  private boolean isModeConsistent(boolean isCustomMessage) {
    // check that we are in consistent mode
    return isCustomMessage != (myCurrentCustomProgressCategory == null);
  }
}
