# Portable Spatial Replacer

Swaps one block for another across a connected area. Machines, pipes and multiblocks are
told about the swap, so a coil upgrade in a running multiblock or a cable tier change
does not break the build.

> Can I retier a whole GregTech pipe/cable run in one click?

Yes. That's the main purpose for this tool.

> Can I replace my AE2 cables or would that break their parts?

You can, the parts stay untouched.

---

## How to replace blocks

Hold the replacer. The hint above your hotbar tells you what the next click does.

### Picking the block

**Shift right click** a block to pick what to replace **with**. The block lands in the
`Replace with` slot and stays there until you pick another one.

### Replacing

- **Point** at a block of the kind you want gone, the preview outlines what would change
- **Right click** to replace it with the target block

Replacing things with GregTech pipes/cables connects them automatically. 
Nothing happens when you point at air, at the block you are already replacing with, or at
a block the tool refuses to touch.

### Spread mode

- `Direct` spreads through the **6 face neighbours** only, within a round radius
- `Diagonal` spreads through all **26 neighbours**, edges and corners included

### Match mode

- `Block only` matches every block of that type
- `Blockstate` matches only blocks in **exactly the same state**, so the same facing, the
  same waterlogging, the same slab half etc

### Radius

Goes from **1** to **128** and changes by one per press. The spread also stops at the
block cap from the config, **1024** by default, whichever comes first.

Some blocks are picked differently:

- A **GregTech pipe** selects the whole pipe net it belongs to
- An **AE2 cable** selects the connected cables of the same kind, and keeps the parts
  attached to them

---

## What it refuses to touch

- Blocks holding **items or fluids**, the whole action is cancelled
- **Indestructible** blocks, meaning anything with a negative hardness, like bedrock
- Anything matched by the **blacklist** in the config

The blacklist accepts block ids, tags, globs and the operators `!`, `&`, `|`, `^`, for
example `gtceu:*_casing` or `#forge:ores`. Ores and raw material blocks are on it by
default.

---

## Undo

Hold the replacer **in your offhand** and right click.

Undo puts the original blocks back with the block entity data they had before the swap,
and takes back the blocks it placed. You need the **original blocks** available.

It **refuses** to run when you are in a **different dimension** than the replacement,
when any of the replaced **blocks changed** after the fact, or when there is **no room**
for the returned items. Undo **costs energy** as well.

---

## Where materials come from

Materials are pulled in this order until the cost is covered:

1. Your inventory
2. Your linked AE2 network

Removed blocks go back to the network first and to your inventory second.

The mod does **not** check wireless range, so the network is reachable from any distance
and any dimension, as long as the access point is loaded. The tooltip and the GUI show
whether a link is set.

---

## Context menu

Press `Left Alt` (rebindable) while holding the replacer to open the menu. Its title line
shows the current radius and spread mode.

- **Radius down** and **Radius up** change the radius by one
- **Spread mode** switches between `Direct` and `Diagonal`
- **Match mode** switches between `Block only` and `Blockstate`

---

## The GUI

**Shift right click air** to open the menu.

The left panel holds the `Replace with` slot, the radius with its `-` and `+` buttons and
the two mode buttons. The right panel repeats the picked block, the current spread mode,
the block cap from the config and the AE2 link status.

Four slots on the left take energy upgrades, `ae2:energy_card` by default and configurable.

---

## Power

The replacer runs on **FE** stored inside the item. A replacement costs:

```
base * distance * multiplier * 5
```

By default, that is cost factor **1**, multiplier **1** and a capacity of
**200,000 FE**. Distance is the distance of each block to the **block you clicked**.
While you aim, the HUD shows the running price as `Replace: x FE`.

Every energy upgrade adds another full capacity on top. When the tool **cannot pay**,
nothing happens and the HUD shows what was needed against what you have.

Set `usePower` to false in the config to make every spatial tool work for free.
