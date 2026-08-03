# Portable Spatial Piper

Pick one block, draw a route in the world and let the piper place the whole run for you.
Made for long pipe or cable runs, floors and walls.

> Can I place GregTech pipes and automatically set their direction?

Yes, in the **context menu** press the `set pipe direction` button.

---

## How to build a route

Hold the piper. The hint above your hotbar tells you what the next click does.

### Picking the block

**Shift right click** a block while the route is **empty**. The block lands in the
`Build with` slot and the piper places it from then on.

Once the route has points, **shift right click** confirms the route and builds it. 

### Selection mode: default

- **Right click** a block to add a route point.
- **Repeat** untill the route is complete.
- **confirm** and **build** the route with **shift right click**

### Selection mode: block in front

- **Right click** anywhere to add a route point 3 blocks ahead of you
- **Repeat** untill the route is complete.
- **confirm** and **build** the route with **shift right click**

### The preview

- **green** boxes are the route you already confirmed
- **orange** is the segment leading to the point you are aiming at right now
- **red** marks positions that are blocked and would be **skipped**

---

## Building

**Shift right click** with at least one route point set. The click works on a block or
on air.

- a position that already holds the **same block** is skipped
- **air** and blocks that **can be replaced** are built
- anything else is **blocked** and skipped

Materials are checked **before** anything is placed. If you are short on any of them the
whole build is refused and **no energy** is spent. The same goes for energy: not enough
power means nothing happens.

The HUD then reports how many blocks were placed and how many were skipped.

---

## Undo

Hold the piper **in your offhand** and right click.

With points on the route this takes the **last point** back. With an empty route it
**undoes the last build** instead.

Undo removes the blocks the last build placed and refunds them to your/connected
inventory. It **refuses** to run when you are in a **different dimension** than the
build, when any of the placed **blocks changed** after the build, or when there is **no
room** for the refunded items.

Refunds go to your linked storage first and to your inventory second. Undo **costs
energy** as well.

---

## Fill mode

- `Path` builds a line through every point in order, with **no limit** on how many points
  you set
- `Fill` treats the points as corners of a box: **2** points make a line, **3** make a
  flat platform, **4** make a full box. 

Switching the mode **clears** the current route.

---

## Where materials come from

Materials are pulled in this order until the cost is covered:

1. Your inventory
2. Your linked AE2 network

The mod does **not** check wireless range, so the network is reachable from any distance
and any dimension, as long as the access point is loaded. The tooltip and the GUI show
whether a link is set.

---

## Context menu

Press `Left Alt` (rebindable) while holding the piper to open the menu.

- **Selection mode** switches between picking the block you click and picking a spot
  3 blocks ahead of you
- **Fill mode** switches between `Path` and `Fill`
- **Pipe direction** cycles off, along path and against path. Disabled unless the
  `Build with` slot holds a GregTech item or fluid pipe
- **Cancel selection** empties the `Build with` slot and drops the route. Disabled when
  no block is picked

---

## The GUI

**Shift right click air** with an **empty route** to open the menu.

It shows the `Build with` slot with the picked block, how many route points you have,
how many blocks the route covers, the block cap from the config and the AE2 link status.
The `Cancel selection` button clears the picked block.

Four slots on the left take energy upgrades, `ae2:energy_card` by default and configurable.

---

## Power

The piper runs on **FE** stored inside the item. A build costs:

```
base * distance * multiplier * 5
```

By default, that is cost factor **1**, multiplier **1** and a capacity of
**200,000 FE**. Distance is the distance of each block to the **first route point**.
While you draw a route the HUD shows the running price as `Build: x FE`.

Every energy upgrade adds another full capacity on top. When the tool **cannot pay**,
nothing happens and the HUD shows what was needed against what you have.

Set `usePower` to false in the config to make every spatial tool work for free.
