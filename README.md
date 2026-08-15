# WaterloggedJumpFix

WaterloggedJumpFix is a small, dependency-free Paper plugin that works around
[MC-8959](https://bugs.mojang.com/browse/MC-8959): players can receive an
automatic upward boost when moving against a block while in water. It targets
the waterlogged slab and stair reproduction reported as
[MC-174654](https://bugs.mojang.com/browse/MC-174654), including the case where
Auto-Jump is disabled and the obstacle is too tall to climb.

## Requirements

- Paper 26.1.2 build 74 (probably works on newer builds)
- Java 25 or newer

## Install

1. Obtain `WaterloggedJumpFix-x.x.x.jar` from a successful GitHub Actions run,
   or build it locally as described below.
2. Put the JAR in the server's `plugins/` directory.
3. Fully restart the server. Avoid `/reload` and plugin hot-reloaders.
4. Confirm that the console logs
   `MC-8959 waterlogged slab/stair workaround enabled globally`.

The plugin has no commands, permissions, dependencies, configuration, or
per-world restrictions.

## How it works

The cancellation is deliberately narrower than a general anti-jump plugin:

1. `PlayerInputEvent` remembers deliberate jump input for two ticks so a quick
   press is not lost because of packet ordering.
2. `EntityKnockbackEvent` and `PlayerVelocityEvent` grant three ticks of
   protection to combat knockback, explosions, fishing rods, and other
   server-sent velocity.
3. When Paper fires `PlayerJumpEvent`, the plugin requires all of the following:
   - Survival or Adventure mode;
   - no current or recent jump input;
   - no current or recent external velocity;
   - no flight, gliding, riptide, swimming, vehicle, or Levitation state;
   - water contact with a waterlogged slab or stair beneath the lower player
     hitbox; and
   - horizontal movement input that points into a collision.
4. The matching jump is cancelled. Paper returns the player to the pre-hop
   position; the plugin preserves any camera turn included in the same movement
   update.

Normal jumps, ordinary water movement, and other waterlogged block types are
left unchanged.

## Expected limitations

- This is a server-side correction. The vanilla client may predict the boost
  before receiving Paper's correction, so a brief bob or rubber-band can still
  be visible, particularly with high latency or while continuously holding
  movement against the wall.
- The short external-velocity exemption intentionally favors correct PvP
  knockback. An automatic water boost that happens during that exemption may be
  allowed through.
- Jump intent is the latest input reported by the client, not direct keyboard
  access. Test deliberate jumping and the unwanted boost with the exact client
  and Paper build used by the server.
- The workaround depends on Paper detecting this movement as
  `PlayerJumpEvent`. If that changes in a future Minecraft or Paper release, the
  plugin will need to move its detector to the movement-validation path.

## Suggested test pass

Before using the plugin in a live match, verify these cases on a test server:

1. Walk and sprint straight and diagonally into a full wall from waterlogged
   bottom slabs and stairs without pressing jump.
2. Repeat while deliberately holding or tapping jump.
3. Receive melee, projectile, explosion, fishing-rod, and plugin-applied
   knockback in the same locations.
4. Repeat at representative player latencies and with the server's anti-cheat
   enabled.

## Build locally

The checked-in Gradle wrapper downloads Gradle 9.4.1 and verifies its SHA-256
checksum. Install a Java 25 JDK, then run:

```bash
./gradlew clean build
```

The deployable file is written to:

```text
build/libs/WaterloggedJumpFix-1.0.0.jar
```

Unit tests run as part of `build` and of the GitHub Actions workflow.
