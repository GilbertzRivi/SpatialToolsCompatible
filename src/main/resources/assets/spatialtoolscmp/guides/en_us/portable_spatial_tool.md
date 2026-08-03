# Portable Spatial Tool

One item that turns into any of the four spatial gadgets, so they stop taking four hotbar
slots.

> Do I have to link every tool to AE2 separately?

No. The AE2 link is shared, so linking once is enough for the cloner, the replacer and
the piper.

---

## Picking a tool

**Right click** the item, or press `Left Alt` (rebindable) to open the **context menu**.
Both show a dropdown with the four modes. Pick one and the item becomes that gadget: same
controls, same GUI, same undo.

The dropdown sits above the tool GUI and above the context menu panels.

Until you pick a mode the item does nothing else. It reads `No tool selected` and holds
no energy.

---

## What each mode keeps

Every mode keeps its **own** settings, so switching back and forth does not lose your configuration.

These are **shared** by all modes:

- the energy inside the item
- the energy upgrades
- the crafting card
- the AE2 link

The item is always named `Portable Spatial Tool`. The tooltip line `Tool:` tells you which
gadget is active.

---

## Power

One energy buffer shared by every mode, so the amount you carry never depends on the mode
you picked. Every energy upgrade adds another full capacity
on top.

Every mode spends energy the way that gadget does, with that gadget's settings from the
config.

Set `usePower` to false in the config to make every spatial tool work for free.
