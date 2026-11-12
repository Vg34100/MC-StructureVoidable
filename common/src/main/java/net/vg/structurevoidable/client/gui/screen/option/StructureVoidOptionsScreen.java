package net.vg.structurevoidable.client.gui.screen.option;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21 {
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.layouts.LinearLayout;
//? } else {
/*import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.OptionsList;
*///? }
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.vg.structurevoidable.Constants;

/** Vanilla layout and explicit draft/save lifecycle, shared by the existing pages. */
public abstract class StructureVoidOptionsScreen extends OptionsSubScreen {
    private Button done;

    protected StructureVoidOptionsScreen(Screen parent, Component title) {
        super(parent, Minecraft.getInstance().options, title);
    }

    protected boolean hasDraft() { return false; }
    protected void saveChanges() {}

    //? if <1.21 {
    /*protected OptionsList list;

    @Override
    protected void init() {
        list = new OptionsList(minecraft, width, height, 32, height - 32, 25);
        addWidget(list);
        addOptions();
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - (hasDraft() ? 155 : 100), height - 27, hasDraft() ? 150 : 200, 20).build());
        if (hasDraft()) addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> cancel())
                .bounds(width / 2 + 5, height - 27, 150, 20).build());
    }

    protected abstract void addOptions();

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        basicListRender(graphics, list, mouseX, mouseY, delta);
    }
    *///? } else {
    @Override
    protected void addFooter() {
        var footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        done = footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .width(hasDraft() ? 150 : 200).build());
        if (hasDraft()) footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> cancel()).width(150).build());
    }
    //? }

    protected void addNavigationButtons(List<AbstractWidget> buttons) {
        //? if >=1.21 {
        list.addSmall(buttons);
        //? } else {
        /*for (int index = 0; index < buttons.size(); index++) {
            AbstractWidget button = buttons.get(index);
            button.setX(width / 2 - 155 + (index % 2) * 160);
            button.setY(45 + (index / 2) * 25);
            addRenderableWidget(button);
        }
        *///? }
    }

    @Override
    public void onClose() {
        if (hasDraft()) {
            //? if >=1.21 {
            if (list != null) list.applyUnsavedChanges();
            //? }
            try { saveChanges(); }
            catch (RuntimeException failure) {
                Constants.LOGGER.error("Could not save StructureVoidable settings", failure);
                if (done != null) done.setMessage(Component.translatable("config.save.failed"));
                return;
            }
        }
        super.onClose();
    }

    private void cancel() { super.onClose(); }
}
