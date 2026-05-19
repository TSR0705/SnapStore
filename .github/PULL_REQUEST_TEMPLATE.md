## 📋 Pull Request Type
- [ ] Bug Fix (non-breaking change which fixes an issue)
- [ ] New Feature (non-breaking change which adds functionality)
- [ ] Refactoring (architecture, structural, or variable cleanups)
- [ ] Documentation (updates to guides, READMEs, or comments)

---

## 📖 Description
Provide a clear and concise summary of your changes, the rationale behind them, and what specific codebase classes are affected.

---

## 🧪 Verification & Build Status

- [ ] All Java source files compile cleanly with `./gradlew compileJava`
- [ ] All unit and integration tests pass with `./gradlew test`
- [ ] Clean thread-isolation borders verified (no direct DB access from JavaFX thread, all updates run inside `Platform.runLater()`)

---

## 📸 Presentation & UI Demos
If this PR includes FXML or layout modifications, please attach before/after screenshots or animations to demonstrate the layout.
