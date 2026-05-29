# Welcome to Create Phantom

Create: Phantom expands the package logistics system of Create, adding airborne package delivery and portable multi-channel stock management.

Send packages to a Phantom Port, launch Cargo Phantoms from belts, and manage multiple logistics networks from a single portable ticker.

## Items

### Cargo Phantom

A small airborne courier that can carry a **Create package**.

The crago phantom can be launched from a belt when the belt is fast enough and has enough effective runway length. After delivery, the empty phantom can return to a phantom port or to the player.

### Phantom Port

A package port for **Cargo Phantom** logistics.

It can store packages and cargo phantoms, dispatch matching packages through its special output side, receive arriving packages.

### Tunable Portable Stock Ticker

A portable stock ticker with **switchable storage channels**.

Use it to open a linked logistics network from your inventory. It can hold up to **six** Storage Channel Extension Cards, allowing one item to switch between multiple logistics networks.

### Storage Channel Extension Card

Stores a **logistics network frequency** and **package addresses**.

## Tips

### Takeoff Rules

Cargo phantoms do not launch from every belt. Takeoff depends on both the belt's **absolute speed** and the remaining **effective belt length** in the belt's movement direction.

Effective belt length means the number of belt segments from the Mini Phantom's insertion position to the belt output end. A long belt can still fail to launch if the Mini Phantom is inserted too close to the exit.

| belt speed | belt length | take off |
| :---:  | :---:  | :---: |
| `< 16` | — | No |
| `16 - 31.99` | `>=6` | Yes |
| `32 - 63.99` | `>=5` | Yes |
| `64 - 127.99` | `>=4` | Yes |
| `128 - 255.99` | `>=3` | Yes |
| `>= 256` | `>=2` | Yes |

### Phantom Port Output

Place an andesite funnel on the Phantom Port's special side and connect it to a belt below the funnel. The Phantom Port will provide a cargo phantom with package only when a valid target can be found.

### Manual launch Cargo Phantom

Place the cargo phantom with package on the ground and use a firework to take off.

### Package Addresses

Phantom Port address matching follows Create package address rules. Text after `//` is treated as a comment and ignored by Create: Phantom address matching.

## Compatibility

### Create: Fluid Logistics

The Tunable Portable Stock Ticker can display and request fluid.

## Others

If you want to help translate this mod, make a pull request with the translation files.

## License
Create Phantom is licensed under the BSD-3-Clause. See [LICENSE](LICENSE.txt) for more information.

Certain sections of the code are from the Create-Mobile-Packages mod, which is licensed under the MIT license. See [Create-Mobile-Packages's license](https://github.com/timplay33/Create-Mobile-Packages/blob/mc1.21.1/main/LICENSE) for more information.

