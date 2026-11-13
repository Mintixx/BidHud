# Bid HUD

### Live Minecraft Auctions. Right on Your Screen.

A fully customizable auction HUD for Minecraft that lets you run live item auctions with your friends, community, or livestream audience.

Bid HUD automatically detects incoming `/pay` messages, tracks the highest bidder, displays a live countdown, and announces the winner when the auction ends.

**You choose the item. Your players place the bids. You handle the trade.**

![Minecraft](https://img.shields.io/badge/Minecraft-26.3-brightgreen?style=for-the-badge&logo=minecraft)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen?style=for-the-badge&logo=minecraft)
![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-orange?style=for-the-badge)
![Client Side](https://img.shields.io/badge/Client--Side-Only-blue?style=for-the-badge)

---

## Features

### Live Auction HUD
- Displays the auction item icon and name.
- Shows the highest bid and bidder.
- Live countdown timer with a visual progress bar.
- Red blinking warning during the final seconds.
- Displays SOLD with the winner when the auction ends.
- Optional bid history displayed beneath the auction bar.
- Live preview while customizing your HUD.

### Auction Any Item
- Select items directly from your inventory.
- Search through every item available in the game.
- Set custom item quantities.
- Give items custom display names.
- Hold an item and start an auction instantly.

You don't need to own an item to list it. Select anything from the game's item registry and run your auction.

### Fully Customizable Interface

Open the configuration menu using `Right Shift`.



<img width="1919" height="1007" alt="image" src="https://github.com/user-attachments/assets/fbaa2d45-3c6b-474a-819c-04c725d790f8" />

<img width="1919" height="1001" alt="image" src="https://github.com/user-attachments/assets/a235bb01-3094-424a-88cb-8e0277b6b6c9" />




| Category | Customization |
|---|---|
| Auction | Item, quantity, duration, starting bid, minimum raise, anti-snipe |
| Layout | 9 screen anchors, offsets, scale, dimensions, padding |
| Style | Rounded corners, borders, gradients, shadows, accent stripes |
| Colors | 8 presets, opacity, 16 customizable colors with RGBA sliders |
| Text | Titles, SOLD text, currency symbol, timer and amount formats |
| Elements | Toggle individual HUD elements and timer positions |
| Effects | New-bid flash, bid pop, shake, slide-in, icon animation |
| Chat & Sound | Announcements, message templates, sound effects and volume |
| Advanced | Custom payment regex, raw chat debugging, test bids and reset |

### Streaming Friendly
Designed for livestreams and community events.

- Works with OBS, TikTok Live Studio, and other streaming software.
- Clean overlay that viewers can easily understand.
- Optional public chat announcements.
- Test bids to rehearse auctions without real payments.
- Automatic refund list printed in chat when auctions end or are cancelled.

---

## Installation

**Requirements**
- Minecraft 1.21.4
- Fabric Loader
- Fabric API
- Mod Menu (optional)

**Installation Steps**

1. Install Fabric Loader for Minecraft 1.21.4.
2. Install Fabric API.
3. Download the latest Bid HUD release.
4. Place the Bid HUD `.jar` file inside your Minecraft `mods` folder.
5. Launch Minecraft.

---

## Quick Start

1. Join a supported Minecraft server.
2. Press `Right Shift` to open the Bid HUD menu.
3. Select an item from your inventory or search for one.
4. Configure the starting bid, duration, and minimum raise.
5. Press **Start Auction**.
6. Players send you money using `/pay`.
7. Bid HUD detects incoming payments and updates the highest bid.
8. When the countdown ends, manually give the item to the winner.

**Important:** You are responsible for manually delivering the item and refunding unsuccessful bidders.

---

## Commands

| Command | Description |
|---|---|
| `/bid` | Opens the configuration menu |
| `/bid start [name]` | Starts an auction |
| `/bid end` | Ends the current auction |
| `/bid cancel` | Cancels the current auction |
| `/bid edit` | Opens drag-to-position mode |
| `/bid test <player> <amount>` | Simulates a test bid |
| `/bid reset` | Resets all settings |

You can also change the menu key through Minecraft's Controls settings.

---

## Payment Detection Setup

Bid HUD detects payments by reading the chat messages displayed by your Minecraft server.

The default payment pattern expects messages similar to:

`PlayerName paid you $5K`

If your server uses a different message format:

1. Open the Bid HUD menu.
2. Navigate to **Advanced**.
3. Enable **Show Raw Chat Lines**.
4. Send a test payment or receive a payment.
5. Inspect the raw chat message.
6. Edit the **Pay Regex** setting to match your server's message format.

This allows Bid HUD to work with different server payment-message formats.

---

## How It Works

Bid HUD is a client-side mod.

It reads incoming payment messages from Minecraft chat, identifies the sender and payment amount, and updates the auction HUD accordingly.

It does not require a server-side plugin or modifications to the Minecraft server.

**Auction handling remains manual:** the mod tracks bids and displays information, but does not automatically transfer items or money.

---

## Disclaimer

Bid HUD is an independent third-party Minecraft modification. It is not affiliated with, endorsed by, or officially connected to Mojang, Microsoft, DonutSMP, or any Minecraft server.

The mod only reads chat messages and renders an on-screen interface. It does not automate gameplay or perform transactions.

Always check your server's rules before using client-side modifications.

---

## Credits

**Developed by Mintixx_**

Independent developer building Minecraft modifications, plugins, desktop applications, AI tools, and other software projects.

---

*Bid HUD — Run your auctions. Track every bid.*
