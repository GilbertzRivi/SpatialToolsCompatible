# Portable Spatial Cloner

Select your setup, machine or any other build, store it in a personal library,
and paste it wherever you want. Don't worry, compatible block entities keep their settings.

> Oh, no! I selected the wrong corner first! What now?

- Open the **context menu** (`left alt`).
- Press: `Flip East/West`, `Flip North/South` and `Flip vertically in place`.
- Press all those buttons again but **holding shift**.

> I want to connect my cloner to my storage, how do I do that?

To link the cloner to a regular storage, like a chest, drawer or a storage controller,
hold it in the **offhand** and **shift right click** on the storage.

To link the cloner to AE2 storage, insert it to the `wireless access point`.

> Can I link my cloner to my AE2 and to my chest at the same time? 

No. Linking any storage clears the previous link.

---

## How to copy a structure

Hold the cloner and make sure you do not have any structures selected. 
To deselect a structure: open the menu, press on the structure entry below the search bar, 
select <empty> and finally press Select and close the menu. 

The block you select as the second will be the `origin` point of the structure.

### Selection mode: default

- **Shift right click** a block to select first corner (red).
- Look at the second corner (up to 64 blocks away) and **Right click** to select it (green).
- **Right click** air **to perform clone**. 
- **Shift right click** any block to **restart the selection**.

### Selection mode: block in front

- **Right click** anywhere to select first corner (red).
- **repeat** to select the second corner (green).
- **Right click** air **to perform clone**.
- **Shift right click** any block to **restart the selection**.

### Modifying the selection

To modify your selection before you clone, you can use the **context menu**, (`left alt`).
On the **offsets** panel, select which corner you want to move with the two buttons on the top,
then press on the render below to move the corner in a given direction.

---

## Pasting

**Shift right click air** to open the menu, and select the structure you want to paste.
Press `Select` and close the menu. Now aim at the place where you want to paste the 
structure, and **right click** to perform the paste.

Once a structure is selected, **shift right clicking** on any block opens the menu as well.

To **customize the paste** open either the **context menu** (`left alt`) or normal item menu.
In there you can anchor the structure for easier placement, move, flip, and rotate it.

### Modifying 

Blocks are placed one by one. A block is **skipped** when the target position is **not air** and
**cannot** be **replaced** (grass, snow etc.), or when you do **not have the items** it costs.
The HUD tells you how many blocks were placed and how many were skipped. 

---

## Undo

Hold the cloner **in your offhand** and right click.

Undo removes the blocks from the last paste and refunds them to your/connected inventory.
It **refuses** to run when you are in a **different dimension** than the paste, when any of the pasted
**blocks changed** after the paste, or when there is **no room** for the refunded items.

Refunds go to your linked storage first and to your inventory second.

---

## Where materials come from

Materials are pulled in this order until the cost is covered:

1. Your inventory
2. Your linked storage
3. Containers in your inventory, if the nested mode allows it
4. Containers in your linked storage, if the nested mode allows it

---

## Blocks that are never copied

Bedrock, nether portals, end portals, end gateways, barriers, all command blocks,
structure blocks, any block with a negative hardness, etc.

---

## Size limits

Copying is blocked if the selection box is larger than **1,000,000** positions, even
when most of it is air. There is also a block count limit per tool in the config, and
the cloner has no limit by default. Both are checked before anything happens, so a
selection that is too large costs no energy.

---

## Context menu

Press `Left Alt` (rebindable) while holding the cloner to open the menu.

- **Offset** moves the stored structure with the 3D direction star.
- **Modify selection** lets you pick the red or green corner and move it.
- **Transform** has the same rotate and flip actions as the GUI, with Shift for **around origin**
- **Options** holds the rest:
    - **Selection mode** switches between picking the block you click and picking a
      block 3 blocks ahead of you, which is how you select air or spots you cannot reach.
    - **Anchor** freezes the paste position at the spot you are aiming at, so you can
      walk around while lining the structure up.
    - **Cancel selection** drops the current corners.
    - **Nested items** is the same cycle as the GUI button.

---

## The GUI

**Shift right click air** to open the menu. With a structure already selected you can
**shift right click** any block as well.

### Structure library

The dropdown in the top left holds every structure you saved. Press the name bar below
the search box to open it.

- The **search box** filters the list as you type.
- Press a row to highlight it, then press `Select` or `Enter` to load it into the tool.
- Selecting `<empty>` clears the tool, so it holds **no structure**.
- The small red **x** on a row **deletes** that structure.
- `Rename` gives the highlighted structure the name from the text box, cut to 32 characters.
- `Escape` closes the dropdown.

#### Folders:

- The **folder button** next to the search box creates a folder. Type a name, then press
  `Create` or `Enter`
- Press a folder row to enter it, and the back row to leave it.
- `Move` puts the highlighted structure into a folder. Press `Move`, then press the
  target folder. `Escape` cancels
- When the structure already sits in a folder, the same button reads `Un-folder` and
  moves it back to the root.
- A folder can only be deleted when it is **empty**.

#### Sharing:

- `Export` writes the highlighted structure to a `.stcstr` file through a normal save
  dialog.
- `Import` reads such a file back and adds it to your library. Files above **16 MB** are
  rejected.

The library is stored **per player on the server**, so it's safe even if you lose the item.

### Transform and offset

The transform row rotates and flips the stored structure:

- `Rotate clockwise`
- `Flip East/West`
- `Flip North/South`
- `Flip vertically`

Press those buttons while **holding shift** to get the **around origin** version.
The tooltips tell you which variant you are about to use.

### Material list

The list on the left shows everything a paste would cost.

- Entries you can afford are listed last, **missing** ones first
- A **craft button** shows up on a row when the item is missing, AE2 can craft it,
  and the item has the **crafting card** upgrade installed. Press it to open the
  AE2 crafting window with the missing amount filled in.

### Nested inventory button

Cycles through four states that decide whether items inside shulker boxes and other
containers count as available:

Containers inside an AE2 network are **always ignored**, no matter which state you pick.

### Craft All

Shows up only with a **crafting card** upgrade installed, and the `Crafting Buffer` accessible
on the linked network.

An **orange border** means no `Crafting Buffer` was found. A **green border** means
a crafting job is **already scheduled**. The button stays inactive when nothing on the list is missing or craftable.

`Craft All` queues every missing material at once, and the buffer holds the results until
the whole order is ready.

### Upgrade slots

Four slots take energy upgrades, `ae2:energy_card` by default and configurable. The
fifth slot takes a **Crafting Card** and enables autocrafting.

---

## Power

The cloner runs on **FE** stored inside the item. Both copying and pasting cost:

```
base * distance * multiplier
```

By default, that is cost factor **1**, multiplier **1** and a capacity of **200,000 FE**.
Distance is the distance of each block to the corner you selected first.
Every energy upgrade adds another full capacity on top. When the tool **cannot pay**,
nothing happens and the HUD shows what was needed against what you have.

Set `usePower` to false in the config to make every spatial tool work for free.
