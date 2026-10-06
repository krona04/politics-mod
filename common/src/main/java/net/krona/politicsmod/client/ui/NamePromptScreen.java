package net.krona.politicsmod.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/**
 * A small window asking for a name (country or city): title, label, input, confirm button and the
 * naming rules. The button stays disabled until the name follows the rules the server checks.
 */
public abstract class NamePromptScreen extends PoliticsScreen {
    /** Same rule as PoliticsManager.isValidName. */
    private static final Pattern VALID = Pattern.compile("[\\p{L}\\p{N} _-]{2,24}");
    private static final int PW = 250, PH = 118;

    private final Component header;
    private final Component label;
    private final Component hint;
    private final Component confirm;
    private EditBox input;
    private FlatButton btnConfirm;
    private int px, py;

    protected NamePromptScreen(Component title, Component header, Component label, Component hint, Component confirm) {
        super(title);
        this.header = header;
        this.label = label;
        this.hint = hint;
        this.confirm = confirm;
    }

    /** Called with a valid name; the screen closes afterwards. */
    protected abstract void submit(String name);

    @Override
    protected void init() {
        super.init();
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        String previous = input != null ? input.getValue() : "";
        input = addRenderableWidget(new EditBox(font, px + 13, py + 41, PW - 26, 14, label));
        input.setMaxLength(24);
        input.setValue(previous);
        input.setHint(hint.copy().withStyle(ChatFormatting.DARK_GRAY));
        setInitialFocus(input);
        int bw = Math.max(100, FlatButton.widthFor(confirm));
        btnConfirm = addRenderableWidget(new FlatButton(px + (PW - bw) / 2, py + PH - 25, bw, 16, confirm, FlatButton.Style.PRIMARY, this::confirm));
        updateButton();
    }

    private boolean valid(String name) {
        return name.strip().equals(name) && VALID.matcher(name).matches();
    }

    private void updateButton() {
        btnConfirm.active = valid(input.getValue());
    }

    private void confirm() {
        String name = input.getValue();
        if (!valid(name)) return;
        submit(name);
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        updateButton();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter confirms, like in vanilla prompts
        if ((keyCode == 257 || keyCode == 335) && btnConfirm.active) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        UiKit.panel(g, px, py, PW, PH);
        g.drawCenteredString(font, header.copy().withStyle(ChatFormatting.BOLD), px + PW / 2, py + 10, UiKit.ACCENT);
        g.fill(px + 12, py + 22, px + PW - 12, py + 23, UiKit.PANEL_BORDER);
        g.drawString(font, label, px + 13, py + 30, UiKit.TEXT_DIM, false);
        // The naming rules, in muted text under the input
        UiKit.wrap(g, font, Component.translatable("message.politicsmod.found.invalid_name"), px + 13, py + 60, PW - 26, 2,
                input != null && !input.getValue().isEmpty() && !valid(input.getValue()) ? UiKit.BAD : UiKit.TEXT_MUTED);
    }
}
