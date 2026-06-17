# Sprites

Fabric mod that exports every Minecraft item icon as a PNG sprite.

On first render tick after loading a world, it:
1. Renders all items using the game's GUI atlas at 4× scale
2. Saves the full atlas as `atlas/atlas.png`
3. Saves UV coordinates for each item as `atlas/items.json`
4. Saves individual sprites as `items/<id>-<name>.png`
5. Commits and pushes the output to the `gh-pages` branch

## Usage

1. Clone the repo
2. Run from IDE or via:
   ```bash
   ./gradlew runClient
   ```
3. Load any world — export runs automatically on the first frame
4. Output lands in `.gh-pages-worktree/` inside the repo and is pushed to `origin/gh-pages`

Requires Java 25+.

## Output format

`atlas/items.json` — array of objects:
```json
[
  { "item": "minecraft:stone", "name": "Stone", "uv": [u0, v0, u1, v1] },
  ...
]
```

UV values are normalized `[0, 1]`, origin at top-left.

## License

CC0 1.0 Universal — public domain. No rights reserved.
