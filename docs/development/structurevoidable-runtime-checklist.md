# StructureVoidable runtime checklist

Use the exact verified installable JAR and its target dependencies in an isolated instance. Cover 26.2 and 1.21.1 on both loaders. Fabric 26.1.1 additionally covers the supplier access widener.

Automated production smoke establishes loader metadata acceptance, common/client initialization, block-entity/renderer registration without startup failure, initial resource reload, a stable first screen, exact staged-JAR class origin and owned-process shutdown. The following visual/gameplay checks require manual review; they are not implied by startup success.

The user reported manual gameplay **PASS** for **26.2 NeoForge** and **1.21.1 NeoForge** after reviewing the conversion. Manual gameplay on **26.1.1 Fabric** is unconfirmed; its packaged-release smoke passed.

- Create a creative world. Confirm the operator items tab defaults on, obtain vanilla Structure Void, and place several adjacent blocks.
- With barrier behavior enabled, hold Structure Void and confirm its marker particles appear. Disable the option and confirm vanilla particle behavior returns.
- Press **Insert** to toggle persistent outline visibility. Press **I** to cycle the selection shape through none, small, medium and large; verify the highlighted selection box for each.
- Enable outline rendering, compare the small/full render box, and check default, void and barrier colors. Inspect adjacent Structure Voids for duplicate outlines or Z-fighting. Move beyond the existing squared-distance cutoff of 128 and return.
- Press **O** through none, default, stone, deepslate, dirt, netherrack and endstone. Confirm none hides the display, default restores outlines, and display blocks match their positions without duplicate drawing. These displays do not replace the real vanilla block.
- Open the config screen through Fabric ModMenu or NeoForge's config entry. Open client/server pages, change an option, return with Done, resize the screen, and confirm settings persist after restart. Check the three key mappings appear in Controls and can be rebound.
- Reload resources with **F3+T** in-world and repeat an outline/display check. Confirm no required mixin, missing renderer or missing block-entity errors occur. Save/reopen the world and inspect the placed Structure Voids again.

The server-named configuration page is existing UI; the migration adds no new settings or server-side gameplay features. Dedicated-server runtime and publication/fresh-machine validation are outside this conversion's client acceptance gate.
