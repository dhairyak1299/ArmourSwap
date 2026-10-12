package com.example.armorswap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = "armorswap", name = "Armor Swap", version = "2.4",
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class ArmorSwap {

    /**
     * A worn piece is replaced only if its durability is BELOW this fraction.
     * Replacement pieces below this fraction are ignored.  0.20 = 20%.
     */
    private static final double MIN_DURABILITY_FRACTION = 0.20;

    /** Ticks to wait between pieces (20 ticks = 1 second). 10 = 0.5 s. */
    private static final int TICKS_BETWEEN_PIECES = 10;

    /** Ticks to wait after the inventory opens before the first click. 6 = 0.3 s. */
    private static final int OPEN_DELAY_TICKS = 6;

    /** Prints progress messages in chat. Set to false once everything works. */
    private static final boolean DEBUG = true;

    private static final int FIRST_ARMOR_CONTAINER_SLOT = 5;
    private static final int LAST_CONTAINER_SLOT = 44;

    private final KeyBinding key = new KeyBinding("Swap armor", Keyboard.KEY_H, "Armor Swap");
    private boolean wasDown = false;

    // swap-in-progress state
    private boolean active = false;
    private int nextPiece = 0;     // 0=helmet ... 3=boots
    private int cooldown = 0;
    private int changed = 0;

    @EventHandler
    public void init(FMLInitializationEvent e) {
        ClientRegistry.registerKeyBinding(key);
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.playerController == null) { active = false; return; }

        boolean down = key.getKeyCode() > 0 && Keyboard.isKeyDown(key.getKeyCode());
        boolean pressed = down && !wasDown;
        wasDown = down;

        if (!active) {
            // One press = one operation. Open the inventory like pressing E.
            if (pressed && mc.currentScreen == null) {
                dbg(p, "H pressed, opening inventory");
                mc.displayGuiScreen(new GuiInventory(p));
                active = true;
                nextPiece = 0;
                changed = 0;
                cooldown = OPEN_DELAY_TICKS;
            }
            return;
        }

        // Player pressed Esc/E or something else took over the screen: stop.
        if (!(mc.currentScreen instanceof GuiContainer)) {
            active = false;
            msg(p, "Cancelled. Screen is now: "
                    + (mc.currentScreen == null ? "none (inventory was closed)" : mc.currentScreen.getClass().getSimpleName()));
            return;
        }

        if (cooldown > 0) { cooldown--; return; }

        // All pieces handled and the last wait is over: close and finish.
        if (nextPiece >= 4) {
            active = false;
            p.closeScreen();
            msg(p, changed == 0 ? "No armor needed changing." : "Replaced " + changed + " piece(s).");
            return;
        }

        // Find the next piece that needs changing (other pieces are skipped instantly).
        while (nextPiece < 4) {
            boolean swapped = processPiece(mc, p, nextPiece);
            nextPiece++;
            if (swapped) {
                changed++;
                cooldown = TICKS_BETWEEN_PIECES;
                break;
            }
        }
    }

    /**
     * Handles one armor slot. Only replaces the worn piece if it is below the
     * durability limit (or the slot is empty), using the highest-durability piece
     * that is itself at or above the limit. Returns true if clicks were sent.
     */
    private boolean processPiece(Minecraft mc, EntityPlayerSP p, int t) {
        Container c = p.inventoryContainer;
        int armorSlot = FIRST_ARMOR_CONTAINER_SLOT + t;
        String name = "Piece " + (t + 6);

        ItemStack worn = c.getSlot(armorSlot).getStack();
        if (worn != null && usable(worn)) {
            dbg(p, name + ": worn piece is fine, left alone");
            return false;
        }

        int bestSlot = -1;
        int bestDur = -1;
        for (int i = FIRST_ARMOR_CONTAINER_SLOT + 4; i <= LAST_CONTAINER_SLOT; i++) {
            ItemStack s = c.getSlot(i).getStack();
            if (s == null || !s.getItem().isValidArmor(s, t, p)) continue;
            if (!usable(s)) continue;               // below the limit: ignored
            int d = remaining(s);
            if (d > bestDur) { bestDur = d; bestSlot = i; }
        }

        if (bestSlot < 0) {
            dbg(p, name + ": needs replacing but no good spare found, left alone");
            return false;
        }

        dbg(p, name + ": swapping slot " + bestSlot + " <-> " + armorSlot);
        click(mc, c, bestSlot);      // pick up the spare
        click(mc, c, armorSlot);     // swap with the worn piece
        click(mc, c, bestSlot);      // put the old piece in the spare's slot
        return true;
    }

    /** True if the piece has at least MIN_DURABILITY_FRACTION left. */
    private boolean usable(ItemStack s) {
        int max = s.getMaxDamage();
        if (max <= 0) return true;
        return (double) (max - s.getItemDamage()) / max >= MIN_DURABILITY_FRACTION;
    }

    private int remaining(ItemStack s) {
        int max = s.getMaxDamage();
        return max <= 0 ? Integer.MAX_VALUE : max - s.getItemDamage();
    }

    private void click(Minecraft mc, Container c, int slot) {
        mc.playerController.windowClick(c.windowId, slot, 0, 0, mc.thePlayer);
    }

    private void dbg(EntityPlayerSP p, String text) {
        if (DEBUG) msg(p, text);
    }

    private void msg(EntityPlayerSP p, String text) {
        p.addChatMessage(new ChatComponentText("\u00a77[ArmorSwap] " + text));
    }
}
