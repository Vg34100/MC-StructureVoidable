# Configuration compatibility fixtures

`legacy-default.properties` represents the old provider's shipped defaults,
including `config.display.block=stone` and the missing `config.block.type`.
Its effective runtime defaults were **display off**, **stone**. New files declare
those defaults correctly without renaming either key.

`legacy-customized.properties` comes from the existing 1.0.2 test instance. It
retains its original mixed LF/CRLF comments and custom outline/disguise values.
The test operates on temporary copies and never modifies the original instance.

Run `python scripts/test-config.py`. It checks the actual config classes using
Java 17-compatible offline fixtures for their three external APIs. Full matrix
compilation checks the real dependencies. The storage tests cover UTF-8, comments,
unknown keys, missing defaults, validated numbers/booleans and refusal to overwrite
broken or externally edited input. Draft tests cover discard, save, reload and
failed-save isolation and inert class loading before the mod loader is ready. Widget clicks, resize layout and in-world visuals remain
manual checks in the runtime checklist.
