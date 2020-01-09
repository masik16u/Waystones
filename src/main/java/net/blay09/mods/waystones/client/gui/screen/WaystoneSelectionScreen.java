package net.blay09.mods.waystones.client.gui.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.client.gui.widget.ITooltipProvider;
import net.blay09.mods.waystones.client.gui.widget.RemoveWaystoneButton;
import net.blay09.mods.waystones.client.gui.widget.SortWaystoneButton;
import net.blay09.mods.waystones.client.gui.widget.WaystoneEntryButton;
import net.blay09.mods.waystones.container.WaystoneSelectionContainer;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.network.NetworkHandler;
import net.blay09.mods.waystones.network.message.RemoveWaystoneMessage;
import net.blay09.mods.waystones.network.message.SelectWaystoneMessage;
import net.blay09.mods.waystones.network.message.SortWaystoneMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WaystoneSelectionScreen extends ContainerScreen<WaystoneSelectionContainer> {

    private final List<IWaystone> waystones;
    private final List<ITooltipProvider> tooltipProviders = new ArrayList<>();

    private Button btnPrevPage;
    private Button btnNextPage;
    private int pageOffset;
    private int headerY;
    private boolean isLocationHeaderHovered;

    public WaystoneSelectionScreen(WaystoneSelectionContainer container, PlayerInventory playerInventory, ITextComponent title) {
        super(container, playerInventory, title);
        waystones = PlayerWaystoneManager.getWaystones(playerInventory.player);
    }

    @Override
    public void init() {
        tooltipProviders.clear();
        btnPrevPage = new Button(width / 2 - 100, height / 2 + 40, 95, 20, I18n.format("gui.waystones.waystone_selection.previous_page"), button -> {
            pageOffset--;
            updateList();
        });
        addButton(btnPrevPage);

        btnNextPage = new Button(width / 2 + 5, height / 2 + 40, 95, 20, I18n.format("gui.waystones.waystone_selection.next_page"), button -> {
            pageOffset++;
            updateList();
        });
        addButton(btnNextPage);

        updateList();
    }

    @Override
    protected <T extends Widget> T addButton(T button) {
        if (button instanceof ITooltipProvider) {
            tooltipProviders.add((ITooltipProvider) button);
        }
        return super.addButton(button);
    }

    private void updateList() {
        final int maxContentHeight = (int) (height * 0.8f);
        final int headerHeight = 40;
        final int footerHeight = 25;
        final int entryHeight = 25;
        final int maxButtonsPerPage = (maxContentHeight - headerHeight - footerHeight) / entryHeight;

        final int buttonsPerPage = Math.max(4, Math.min(maxButtonsPerPage, waystones.size()));
        final int contentHeight = headerHeight + buttonsPerPage * entryHeight + footerHeight;
        headerY = height / 2 - contentHeight / 2;

        btnPrevPage.active = pageOffset > 0;
        btnNextPage.active = pageOffset < (waystones.size() - 1) / buttonsPerPage;

        buttons.removeIf(button -> button instanceof WaystoneEntryButton || button instanceof SortWaystoneButton || button instanceof RemoveWaystoneButton);

        int y = headerHeight;
        for (int i = 0; i < buttonsPerPage; i++) {
            int entryIndex = pageOffset * buttonsPerPage + i;
            if (entryIndex >= 0 && entryIndex < waystones.size()) {
                IWaystone waystone = waystones.get(entryIndex);

                addButton(createWaystoneButton(y, waystone));

                SortWaystoneButton sortUpButton = new SortWaystoneButton(width / 2 + 108, headerY + y + 2, waystone, -1, it -> sortButtonHandler(entryIndex, -1));
                if (entryIndex == 0) {
                    sortUpButton.visible = false;
                }
                addButton(sortUpButton);

                SortWaystoneButton sortDownButton = new SortWaystoneButton(width / 2 + 108, headerY + y + 11, waystone, 1, it -> sortButtonHandler(entryIndex, 1));
                if (entryIndex == waystones.size() - 1) {
                    sortDownButton.visible = false;
                }
                addButton(sortDownButton);

                RemoveWaystoneButton removeButton = new RemoveWaystoneButton(width / 2 + 122, headerY + y + 4, button -> {
                    PlayerWaystoneManager.deactivateWaystone(Minecraft.getInstance().player, waystone);
                    NetworkHandler.channel.sendToServer(new RemoveWaystoneMessage(waystone));
                    updateList();
                });
                addButton(removeButton);

                y += 22;
            }
        }

        btnPrevPage.y = headerY + headerHeight + buttonsPerPage * 22 + (waystones.size() > 0 ? 10 : 0);
        btnNextPage.y = headerY + headerHeight + buttonsPerPage * 22 + (waystones.size() > 0 ? 10 : 0);
    }

    private WaystoneEntryButton createWaystoneButton(int y, IWaystone waystone) {
        IWaystone waystoneFrom = container.getWaystoneFrom();
        WaystoneEntryButton btnWaystone = new WaystoneEntryButton(width / 2 - 100, headerY + y, waystone, container.getWarpMode(), button -> {
            NetworkHandler.channel.sendToServer(new SelectWaystoneMessage(waystone));
        });
        if (waystoneFrom != null && waystone.getWaystoneUid().equals(waystoneFrom.getWaystoneUid())) {
            btnWaystone.active = false;
        }
        return btnWaystone;
    }

    private void sortButtonHandler(int index, int sortDir) {
        int otherIndex = index + sortDir;
        if (index == -1 || otherIndex < 0 || otherIndex >= waystones.size()) {
            return;
        }

        Collections.swap(waystones, index, otherIndex);
        NetworkHandler.channel.sendToServer(new SortWaystoneMessage(index, otherIndex));
        updateList();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (isLocationHeaderHovered && container.getWaystoneFrom() != null) {
            // TODO Waystones.proxy.openWaystoneSettings(Minecraft.getInstance().player, fromWaystone, true);

            return true;
        }

        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        renderBackground();
        super.render(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        for (ITooltipProvider tooltipProvider : tooltipProviders) {
            if(tooltipProvider.shouldShowTooltip()) {
                renderTooltip(tooltipProvider.getTooltip(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        FontRenderer fontRenderer = Minecraft.getInstance().fontRenderer;

        GlStateManager.color4f(1f, 1f, 1f, 1f);
        IWaystone fromWaystone = container.getWaystoneFrom();
        drawCenteredString(fontRenderer, getTitle().getFormattedText(), width / 2, headerY + (fromWaystone != null ? 20 : 0), 0xFFFFFF);
        if (fromWaystone != null) {
            drawLocationHeader(fromWaystone.getName(), mouseX, mouseY, width / 2, headerY);
        }

        if (waystones.size() == 0) {
            drawCenteredString(fontRenderer, TextFormatting.RED + I18n.format("waystones:scrollNotBound"), width / 2, height / 2 - 20, 0xFFFFFF);
        }
    }

    private void drawLocationHeader(String locationName, int mouseX, int mouseY, int x, int y) {
        FontRenderer fontRenderer = Minecraft.getInstance().fontRenderer;

        String locationPrefix = TextFormatting.YELLOW + I18n.format("gui.waystones.waystone_selection.current_location") + " ";
        int locationPrefixWidth = fontRenderer.getStringWidth(locationPrefix);

        int locationWidth = fontRenderer.getStringWidth(locationName);

        int fullWidth = locationPrefixWidth + locationWidth;

        int startX = x - fullWidth / 2 + locationPrefixWidth;
        isLocationHeaderHovered = mouseX >= startX && mouseX < startX + locationWidth + 16
                && mouseY >= y && mouseY < y + fontRenderer.FONT_HEIGHT;

        String fullText = locationPrefix + TextFormatting.WHITE;
        if (isLocationHeaderHovered) {
            fullText += TextFormatting.UNDERLINE;
        }
        fullText += locationName;

        drawString(fontRenderer, TextFormatting.UNDERLINE + fullText, x - fullWidth / 2, y, 0xFFFFFF);

        if (isLocationHeaderHovered) {
            GlStateManager.pushMatrix();
            GlStateManager.translated(x + fullWidth / 2f + 4, y, 0f);
            float scale = 0.5f;
            GlStateManager.scalef(scale, scale, scale);
            Minecraft.getInstance().getTextureManager().bindTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE);
            Minecraft.getInstance().getItemRenderer().renderItemAndEffectIntoGUI(new ItemStack(Items.WRITABLE_BOOK), 0, 0);
            GlStateManager.popMatrix();
        }
    }

}
