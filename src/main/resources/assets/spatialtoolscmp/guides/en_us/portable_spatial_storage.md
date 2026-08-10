# Portable Spatial Storage

Cuts a region out of the world, carries it in the item and pastes it back somewhere else.
Block entities keep their inventories, energy and settings, so a running machine keeps
running after the move.

> Can I move my whole base with one item?

Yes sure, just not at once, do that in chunks and you will be fine.

---

## How to cut a region

Hold the tool. The hint above your hotbar tells you what the next click does.

The block you select the second will be the `origin` point of the structure.

### Selection mode: default

- **Shift right click** a block to select first corner (red).
- Look at the second corner (up to 64 blocks away) and **right click** to select it (green).
- **Right click** air **to perform the cut**.
- **Shift right click** any block to **restart the selection**.

### Selection mode: block in front

- **Right click** anywhere to select first corner (red).
- **Repeat** to select the second corner (green).
- **Right click** air **to perform the cut**.
- **Shift right click** any block to **restart the selection**.

### Modifying the selection

To modify your selection before you cut, use the **context menu** (`left alt`).
On the **offsets** panel, select which corner you want to move with the two buttons on
the top, then press on the render below to move the corner in a given direction.

---

## What gets cut

Everything inside the box except the following:

Bedrock, nether portals, end portals, end gateways, barriers, all command blocks,
structure blocks, any block with a **negative hardness**, etc.

Cutting is refused when the selection box is larger than **1,000,000** positions, even
when most of it is air.

---

## Pasting

With a structure stored, **right click** to place it.

Right click a block and the paste starts at the face you clicked. Right click air and the
game looks for a block up to 64 blocks ahead.

The paste is **all or nothing**. If any block would land outside the world height, or on
a block that is not air and cannot be replaced, **nothing** is placed. Move the paste
position or clear the way and try again.

---

## Undo

Hold the tool **in your offhand** and right click. What undo does depends on the last
action:

- After a **cut** it pastes the structure back where it came from.
- After a **paste** it cuts the structure again.

Undo **refuses** to run when you are in a **different dimension** than the action, and
after a paste also when any of the placed **blocks changed** in the meantime.

---

## Context menu

Press `Left Alt` (rebindable) while holding the tool to open the menu.

- **Offset** moves the stored structure with the 3D direction star.
- **Modify selection** lets you pick the red or green corner and move it.
- **Transform** has the same rotate and flip actions as the GUI, with Shift for **around origin**.
- **Options** holds the rest:
    - **Selection mode** switches between picking the block you click and picking a
      block 3 blocks ahead of you, which is how you select air or spots you cannot reach.
    - **Anchor** freezes the paste position at the spot you are aiming at, so you can
      walk around while lining the structure .
    - **Cancel selection** drops the current corners.

---

## The GUI

**Shift right click air** to open the menu. With a structure already stored you can
**shift right click** any block as well.

The big panel is a 3D preview of the stored structure. Drag with the **left mouse button**
to turn it, **scroll** to zoom. The compass in the corner shows which way the structure
faces and turns with the preview.

### Transform and offset

The transform row rotates and flips the stored structure:

- `Rotate clockwise`
- `Flip East/West`
- `Flip North/South`
- `Flip vertically`

Press those buttons while **holding shift** to get the **around origin** version.
The tooltips tell you which variant you are about to use.

### Upgrade slots

Four slots on the left take energy upgrades, `ae2:energy_card` by default and configurable.

---

## Power

The tool runs on **FE** stored inside the item. Both cutting and pasting cost:

```
base * distance * multiplier
```

By default, that is cost factor **1**, multiplier **1** and a capacity of
**200,000 FE**. Distance is the distance of each block to the corner you selected second.
While you aim, the HUD shows the running price as `Cut: x FE`, in red when the tool
cannot pay it.

Every energy upgrade adds another full capacity on top. When the tool **cannot pay**,
nothing happens and the HUD shows what was needed against what you have.

Set `usePower` to false in the config to make every spatial tool work for free.
