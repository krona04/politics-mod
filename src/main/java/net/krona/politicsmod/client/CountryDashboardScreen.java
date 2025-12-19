package net.krona.politicsmod.client;

import net.krona.politicsmod.network.ClaimLandPayload;
import net.krona.politicsmod.network.ManagePoliticsPayload;
import net.krona.politicsmod.network.UpdateFlagPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CountryDashboardScreen extends Screen {
    private final String countryName;
    private final int balance;
    private final List<String> cities;
    private String flagUrl;

    private int activeTab = 0; // 0 = Обзор, 1 = Карта

    private final int bgW = 250;
    private final int bgH = 240;

    private Button btnTabMain, btnTabMap;
    private EditBox urlInput;
    private Button btnSaveUrl;

    private Button btnCycleCity, btnSetCapital, btnAssignCity, btnAssignWild;

    private double mapOffsetX = 0;
    private double mapOffsetZ = 0;
    private boolean isDraggingMap = false;
    private final Set<ChunkPos> selectedChunks = new HashSet<>();
    private final int mapSize = 130;
    private final int scale = 10;
    private int selectedCityIndex = 0;

    public CountryDashboardScreen(String name, int bal, List<String> cities, String flagUrl) {
        super(Component.translatable("gui.politicsmod.dashboard.title"));
        this.countryName = name;
        this.balance = bal;
        this.cities = cities;
        this.flagUrl = flagUrl;
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int cy = height / 2;
        int bgX = cx - bgW / 2;
        int bgY = cy - bgH / 2;

        int tabsY = bgY + 25;
        this.btnTabMain = Button.builder(Component.translatable("gui.politicsmod.dashboard.tab.main"), b -> switchTab(0))
                .bounds(bgX + 10, tabsY, 110, 20).build();
        this.btnTabMap = Button.builder(Component.translatable("gui.politicsmod.dashboard.tab.map"), b -> switchTab(1))
                .bounds(bgX + 130, tabsY, 110, 20).build();
        this.addRenderableWidget(btnTabMain);
        this.addRenderableWidget(btnTabMap);

        int mainContentY = tabsY + 30;
        this.urlInput = new EditBox(font, bgX + 25, mainContentY + 90, 150, 20, Component.literal("URL"));
        this.urlInput.setMaxLength(256);
        this.urlInput.setValue(this.flagUrl == null ? "" : this.flagUrl);
        this.addRenderableWidget(this.urlInput);

        this.btnSaveUrl = Button.builder(Component.literal("OK"), b -> {
            String newUrl = urlInput.getValue();
            this.flagUrl = newUrl;
            PacketDistributor.sendToServer(new UpdateFlagPayload(countryName, newUrl));
        }).bounds(bgX + 180, mainContentY + 90, 40, 20).build();
        this.addRenderableWidget(this.btnSaveUrl);

        int mapAreaTop = tabsY + 25;
        int mapAreaHeight = mapSize;
        int controlsY = mapAreaTop + mapAreaHeight + 10;

        this.btnCycleCity = Button.builder(Component.translatable("gui.politicsmod.dashboard.target", "..."), b -> cycleTargetCity())
                .bounds(bgX + 10, controlsY, 110, 20).build();
        this.addRenderableWidget(this.btnCycleCity);

        this.btnSetCapital = Button.builder(Component.translatable("gui.politicsmod.dashboard.make_capital"), b -> setCapital())
                .bounds(bgX + 130, controlsY, 110, 20).build();
        this.addRenderableWidget(this.btnSetCapital);

        this.btnAssignCity = Button.builder(Component.translatable("gui.politicsmod.dashboard.assign"), b -> assignToSelectedCity())
                .bounds(bgX + 10, controlsY + 25, 230, 20).build();
        this.addRenderableWidget(this.btnAssignCity);

        this.btnAssignWild = Button.builder(Component.translatable("gui.politicsmod.dashboard.wilderness"), b -> assignToWilderness())
                .bounds(bgX + 10, controlsY + 50, 230, 20).build();
        this.addRenderableWidget(this.btnAssignWild);

        updateCityButtonText();
        updateVisibility();
    }

    private void switchTab(int tab) {
        this.activeTab = tab;
        updateVisibility();
    }

    private void updateVisibility() {
        boolean isMain = (activeTab == 0);
        boolean isMap = (activeTab == 1);

        if (urlInput != null) urlInput.visible = isMain;
        if (btnSaveUrl != null) btnSaveUrl.visible = isMain;

        if (btnCycleCity != null) btnCycleCity.visible = isMap;
        if (btnSetCapital != null) btnSetCapital.visible = isMap;
        if (btnAssignCity != null) btnAssignCity.visible = isMap;
        if (btnAssignWild != null) btnAssignWild.visible = isMap;
    }

    // --- ЛОГИКА ---
    private void cycleTargetCity() { if (!cities.isEmpty()) { selectedCityIndex = (selectedCityIndex + 1) % cities.size(); updateCityButtonText(); } }
    private void setCapital() { if (!cities.isEmpty()) { PacketDistributor.sendToServer(new ManagePoliticsPayload(0, cities.get(selectedCityIndex), "")); onClose(); } }
    private void updateCityButtonText() {
        if (cities.isEmpty()) {
            btnCycleCity.setMessage(Component.translatable("gui.politicsmod.dashboard.no_cities")); btnCycleCity.active = false; btnAssignCity.active = false; btnSetCapital.active = false;
        } else {
            String city = cities.get(selectedCityIndex);
            if (city.length() > 10) city = city.substring(0, 10) + "..";
            btnCycleCity.setMessage(Component.translatable("gui.politicsmod.dashboard.target", city)); btnCycleCity.active = true; btnAssignCity.active = true; btnSetCapital.active = true;
        }
    }
    private void assignToSelectedCity() {
        if (selectedChunks.isEmpty() || cities.isEmpty()) return;
        String target = cities.get(selectedCityIndex);
        for (ChunkPos pos : selectedChunks) PacketDistributor.sendToServer(new ClaimLandPayload(pos.getWorldPosition(), countryName, target, true));
        selectedChunks.clear();
    }
    private void assignToWilderness() {
        if (selectedChunks.isEmpty()) return;
        for (ChunkPos pos : selectedChunks) PacketDistributor.sendToServer(new ClaimLandPayload(pos.getWorldPosition(), countryName, "", false));
        selectedChunks.clear();
    }


    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);

        int cx = width / 2;
        int cy = height / 2;
        int bgX = cx - bgW / 2;
        int bgY = cy - bgH / 2;

        super.render(g, mx, my, pt);

        g.drawCenteredString(this.font, "§l" + countryName, cx, bgY + 10, 0xFFD700);

        if (activeTab == 0) {
            renderMainTab(g, cx, bgY + 50);
        } else {
            renderMapTab(g, mx, my, cx, bgY + 50);
        }
    }

    private void renderMainTab(GuiGraphics g, int cx, int startY) {
        g.drawCenteredString(this.font, Component.translatable("gui.politicsmod.dashboard.balance", "§a" + balance).getString(), cx, startY + 10, 0xFFFFFF);
        if (flagUrl != null && !flagUrl.isEmpty()) {
            ResourceLocation texture = FlagTextureManager.getTexture(flagUrl);
            int size = 64;
            if (texture != null) {
                RenderSystem.setShaderTexture(0, texture);
                g.blit(texture, cx - size/2, startY + 30, 0, 0, size, size, size, size);
            } else {
                g.fill(cx - size/2, startY + 30, cx + size/2, startY + 30 + size, 0xFF555555);
                g.drawCenteredString(font, "...", cx, startY + 30 + size/2 - 4, 0xFFFFFF);
            }
        } else {
            g.drawCenteredString(font, Component.translatable("gui.politicsmod.dashboard.no_flag"), cx, startY + 50, 0xAAAAAA);
        }
        g.drawCenteredString(this.font, Component.translatable("gui.politicsmod.dashboard.paste_url"), cx, startY + 115, 0xAAAAAA);
        int listY = startY + 135;
        g.drawString(this.font, Component.translatable("gui.politicsmod.dashboard.cities_list"), cx - 110, listY, 0xFFFFFF);
        if (cities.isEmpty()) {
            g.drawString(this.font, "-", cx - 100, listY + 15, 0xAAAAAA);
        } else {
            for (int i = 0; i < Math.min(cities.size(), 3); i++) {
                g.drawString(this.font, "• " + cities.get(i), cx - 100, listY + 15 + (i * 12), 0xAAAAAA);
            }
        }
    }

    private void renderMapTab(GuiGraphics g, int mouseX, int mouseY, int cx, int startY) {
        int mapX = cx - (mapSize / 2);
        int mapY = startY;
        g.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY + mapSize + 2, 0xFF5A4530);
        g.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFFE8DCCA);
        g.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
        var chunks = ClientPoliticsData.getChunks();
        if (this.minecraft.player != null) {
            ChunkPos playerChunk = this.minecraft.player.chunkPosition();
            int centerScreenX = mapX + (mapSize / 2);
            int centerScreenY = mapY + (mapSize / 2);
            for (var entry : chunks.entrySet()) {
                int color = entry.getValue();
                int renderColor = 0xAA000000 | (color & 0xFFFFFF);
                drawChunkOnMap(g, playerChunk, centerScreenX, centerScreenY, entry.getKey(), renderColor);
            }
            for (ChunkPos selected : selectedChunks) drawChunkOnMap(g, playerChunk, centerScreenX, centerScreenY, selected, 0x99FFFFFF);
            g.fill(centerScreenX - 2, centerScreenY - 2, centerScreenX + 3, centerScreenY + 3, 0xFFFF0000);
        }
        g.disableScissor();
        if (mouseX >= mapX && mouseX < mapX + mapSize && mouseY >= mapY && mouseY < mapY + mapSize && this.minecraft.player != null) {
            ChunkPos playerChunk = this.minecraft.player.chunkPosition();
            int centerScreenX = mapX + (mapSize / 2);
            int centerScreenY = mapY + (mapSize / 2);
            double relativeX = mouseX - centerScreenX - (mapOffsetX * scale);
            double relativeY = mouseY - centerScreenY - (mapOffsetZ * scale);
            int chunkDx = (int) Math.floor(relativeX / scale);
            int chunkDz = (int) Math.floor(relativeY / scale);
            ChunkPos hoveredChunk = new ChunkPos(playerChunk.x + chunkDx, playerChunk.z + chunkDz);
            if (chunks.containsKey(hoveredChunk)) {
                int col = chunks.get(hoveredChunk);

                Component tooltipText = (col == 0x00AA00) ?
                        Component.translatable("gui.politicsmod.map.country") :
                        Component.translatable("gui.politicsmod.map.city");

                g.renderTooltip(this.font, tooltipText, mouseX, mouseY);
            }
        }
    }

    private void drawChunkOnMap(GuiGraphics g, ChunkPos playerPos, int centerX, int centerY, ChunkPos targetPos, int color) {
        double dx = (targetPos.x - playerPos.x) + mapOffsetX;
        double dz = (targetPos.z - playerPos.z) + mapOffsetZ;
        int boxX = (int) (centerX + (dx * scale));
        int boxY = (int) (centerY + (dz * scale));
        if (boxX > centerX - mapSize && boxX < centerX + mapSize && boxY > centerY - mapSize && boxY < centerY + mapSize) {
            g.fill(boxX + 1, boxY + 1, boxX + scale - 1, boxY + scale - 1, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeTab == 1 && button == 0) {
            int cx = width / 2;
            int cy = height / 2;
            int bgY = cy - bgH / 2;
            int mapX = cx - (mapSize / 2);
            int mapY = bgY + 50;
            if (mouseX >= mapX && mouseX < mapX + mapSize && mouseY >= mapY && mouseY < mapY + mapSize) {
                if (this.minecraft.player != null) {
                    ChunkPos playerChunk = this.minecraft.player.chunkPosition();
                    int centerScreenX = mapX + (mapSize / 2);
                    int centerScreenY = mapY + (mapSize / 2);
                    double relativeX = mouseX - centerScreenX - (mapOffsetX * scale);
                    double relativeY = mouseY - centerScreenY - (mapOffsetZ * scale);
                    int chunkDx = (int) Math.floor(relativeX / scale);
                    int chunkDz = (int) Math.floor(relativeY / scale);
                    ChunkPos clickedChunk = new ChunkPos(playerChunk.x + chunkDx, playerChunk.z + chunkDz);
                    if (selectedChunks.contains(clickedChunk)) selectedChunks.remove(clickedChunk);
                    else selectedChunks.add(clickedChunk);
                    isDraggingMap = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingMap = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeTab == 1 && isDraggingMap) {
            mapOffsetX += dragX / scale;
            mapOffsetZ += dragY / scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}