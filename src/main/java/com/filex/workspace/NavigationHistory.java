package com.filex.workspace;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded navigation history for investigation workspace.
 *
 * <p>Maintains a stack of workspace snapshots for back/forward navigation. Implements bounded
 * pruning to prevent unbounded memory growth.
 *
 * <p>Thread-safety: NOT thread-safe. Must be synchronized externally.
 */
public final class NavigationHistory {

  private static final int MAX_HISTORY_SIZE = 50;

  private final Deque<WorkspaceState> backStack = new ArrayDeque<>();
  private final Deque<WorkspaceState> forwardStack = new ArrayDeque<>();
  private WorkspaceState current;

  public NavigationHistory(WorkspaceState initialState) {
    this.current = Objects.requireNonNull(initialState, "initialState must not be null");
  }

  /**
   * Navigates to a new state, pushing current state to back stack. Clears forward stack (new
   * navigation invalidates forward history).
   *
   * @param newState the new workspace state
   */
  public void navigateTo(WorkspaceState newState) {
    Objects.requireNonNull(newState, "newState must not be null");

    backStack.push(current);
    current = newState;
    forwardStack.clear();

    pruneIfNeeded();
  }

  /**
   * Navigates back to previous state if available.
   *
   * @return previous state if available
   */
  public Optional<WorkspaceState> navigateBack() {
    if (backStack.isEmpty()) {
      return Optional.empty();
    }

    forwardStack.push(current);
    current = backStack.pop();

    return Optional.of(current);
  }

  /**
   * Navigates forward to next state if available.
   *
   * @return next state if available
   */
  public Optional<WorkspaceState> navigateForward() {
    if (forwardStack.isEmpty()) {
      return Optional.empty();
    }

    backStack.push(current);
    current = forwardStack.pop();

    return Optional.of(current);
  }

  /** Returns current workspace state. */
  public WorkspaceState current() {
    return current;
  }

  /** Returns true if back navigation is available. */
  public boolean canNavigateBack() {
    return !backStack.isEmpty();
  }

  /** Returns true if forward navigation is available. */
  public boolean canNavigateForward() {
    return !forwardStack.isEmpty();
  }

  /** Returns the size of back history. */
  public int backStackSize() {
    return backStack.size();
  }

  /** Returns the size of forward history. */
  public int forwardStackSize() {
    return forwardStack.size();
  }

  /** Clears all navigation history. */
  public void clear() {
    backStack.clear();
    forwardStack.clear();
  }

  /** Prunes history if it exceeds maximum size. Removes oldest entries from back stack. */
  private void pruneIfNeeded() {
    while (backStack.size() > MAX_HISTORY_SIZE) {
      backStack.removeLast();
    }
  }
}
