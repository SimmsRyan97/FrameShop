# FrameShop

FrameShop turns item frames and signs into a clean, immersive shopping experience that feels right at home in any Minecraft world. Instead of clunky shop systems, your players get a visual market that is easy to use and genuinely fun to interact with.

## Features

### Visual, world-friendly shops

- **Natural market style**: Shops are built directly into your world using item frames and signs.
- **Easy to understand**: Players can see exactly what they are buying at a glance.
- **Great for towns and spawn markets**: Build shopping districts that feel alive and intentional.

### Fast and flexible buying

- **Quick amount controls**: Players can adjust by `-stack`, `-1`, `+1`, or `+stack` in the GUI.
- **Custom amount input**: Buyers can type an exact amount for full control.
- **Smooth purchase flow**: The process is simple, fast, and friendly for both new and experienced players.

### Economy-ready experience

- **Automatic price lookup**: Item prices come from Essentials worth values.
- **Built-in economy support**: Works with Vault economy providers.
- **Tax support**: Add a configurable tax percentage to shape your server economy.

### Enchantment pricing support

FrameShop now supports enchantment bonus pricing using your existing `Essentials/worth.yml` file.

- Keep your normal base item price, for example `enchantedbook` or `diamondsword`.
- Add optional enchant bonus keys under the same `worth:` section.
- Bonus values are added on top of the base price.

Supported key formats:

- `<item>|<enchant>|<level>`: exact item + exact enchant + exact level
- `<item>|<enchant>`: exact item + exact enchant for any level
- `any|<enchant>|<level>`: global enchant bonus for that level
- `any|<enchant>`: global enchant bonus for any level

Use lowercase material/enchant names (without spaces). You can use either namespaced enchant keys (like `minecraft:sharpness`) or short keys (like `sharpness`).

Example:

```yml
worth:
	enchantedbook: 89.3
	enchantedbook|sharpness|5: 650
	enchantedbook|mending: 900
	diamondsword: 120
	diamondsword|sharpness|5: 500
	any|unbreaking|3: 150
```

In this example:

- A Sharpness V enchanted book costs `89.3 + 650`
- A Mending enchanted book costs `89.3 + 900`
- A Sharpness V diamond sword costs `120 + 500`
- Any item with Unbreaking III gets `+150`

### Clear feedback for players

- **Helpful messages**: Players get clear responses for success, invalid amounts, low balance, and inventory limits.
- **Readable chat formatting**: Messages are styled for clarity and quick understanding.
- **Reliable sign states**: Shop signs visibly update when frames are active or inactive.

## Why server owners like FrameShop

- It makes your economy feel more active without overwhelming players.
- It keeps shopping immersive instead of pulling players out of the world.
- It is simple for players to use and easy to manage day to day.

## Bug Reports and Suggestions

Feedback helps FrameShop keep getting better. If you find a bug or want to suggest an improvement, share it and help shape future updates.

## Final Word

If you want a shop plugin that feels modern, intuitive, and built for real player interaction, **FrameShop** is a strong fit for your server.
