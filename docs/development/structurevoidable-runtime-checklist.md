# StructureVoidable runtime checklist

Use the exact verified installable JAR and its target dependencies in an isolated instance. Cover the new 1.20.1 Fabric/Forge and 26.3 Fabric/NeoForge boundaries, existing 1.21.1 Fabric/NeoForge, early NeoForge and Fabric 26.1.1's supplier access widener. The automated set is selected by matrix `release_smoke` flags; see the session summary for results.

Automated production smoke establishes loader metadata acceptance, common/client initialization, block-entity/renderer registration without startup failure, initial resource reload, a stable first screen, exact staged-JAR class origin and owned-process shutdown. The following visual/gameplay checks require manual review; they are not implied by startup success.

The user confirmed manual gameplay **PASS** for the published 1.0.3 artifacts on **26.2 NeoForge**, **1.21.1 NeoForge** and **26.1.1 Fabric**. Those are historical release results, not manual acceptance of this new port. The checks below still require visual/UI/world review on the changed artifacts.

- Create a creative world. Confirm the operator items tab defaults on, obtain vanilla Structure Void, and place several adjacent blocks.
- With barrier behavior enabled, hold Structure Void and confirm its marker particles appear. Disable the option and confirm vanilla particle behavior returns.
- Press **Insert** to toggle persistent outline visibility. Press **I** to cycle the selection shape through none, small, medium and large; verify the highlighted selection box for each.
- Enable outline rendering, compare the small/full render box, and check default, void and barrier colors. Inspect adjacent Structure Voids for duplicate outlines or Z-fighting. Move beyond the existing squared-distance cutoff of 128 and return.
- Press **O** through none, default, stone, deepslate, dirt, netherrack and endstone. Confirm none hides the display, default restores outlines, and display blocks match their positions without duplicate drawing. These displays do not replace the real vanilla block.
- Open the config screen through Fabric ModMenu or Forge/NeoForge's native config entry. Open client/server pages. Change each boolean/choice and confirm Done or Escape saves, Cancel discards, and resizing retains the draft. Restart and confirm saved settings persist. After cycling O to none/default, open and close settings and confirm that state is retained. With an externally edited/unwritable config, confirm the failed-save message leaves the screen open and Cancel still returns to the parent. Check the three key mappings appear in Controls and can be rebound.
- Reload resources with **F3+T** in-world and repeat an outline/display check. Confirm no required mixin, missing renderer or missing block-entity errors occur. Save/reopen the world and inspect the placed Structure Voids again.

The server-named configuration page is existing UI; the migration adds no new settings or server-side gameplay features. Dedicated-server smoke complements the bytecode side check: common bootstraps must start with all client factory/screen classes present in the JAR, without linking those classes. It must reach Done, accept stop and exit successfully. Publication, version bumping and fresh-machine tooling remain separate follow-ups.

For the 16-target release, the user confirmed startup in Prism Launcher on
1.20.1 Forge and 26.3 Fabric and accepted initial publication. Per-feature visual
and settings checks still need deeper testing; startup is not full gameplay QA.
