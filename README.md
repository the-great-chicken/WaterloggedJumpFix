# WaterloggedJumpFix

WaterloggedJumpFix is a small, runtime-dependency-free Paper plugin that works around [MC-8959](https://bugs.mojang.com/browse/MC-8959): players can receive an automatic upward boost when moving against a block while in water. It targets the waterlogged slab and stair reproduction reported as [MC-174654](https://bugs.mojang.com/browse/MC-174654), including the case where Auto-Jump is disabled and the obstacle is too tall to climb.

## Requirements

- Paper 26.1.2
- Java 25 or newer

## Install

1. Obtain `WaterloggedJumpFix-x.x.x.jar` from a successful GitHub Actions run, or build it locally as described below.
2. Put the JAR in the server's `plugins/` directory.
3. Fully restart the server. Avoid `/reload` and plugin hot-reloaders.
4. Confirm that the console logs `MC-8959 workaround enabled globally`.

The plugin has no commands, permissions, runtime dependencies, or per-world restrictions.

## Configuration

The generated `plugins/WaterloggedJumpFix/config.yml` contains:

```yaml
prediction-distance: 0.0
```

The conservative default disables prediction. Motion suppression begins only after the unwanted jump has been positively identified and cancelled once. Set a small positive distance, such as `0.02`, to begin suppression when a collision is that many blocks ahead in the current input direction. This may prevent the first correction, but it can act on input that the player changes while packets are in flight. Accepted values are from `0.0` to `1.0` blocks.

## How it works

The cancellation is deliberately narrower than a general anti-jump plugin:

1. `PlayerInputEvent` remembers deliberate jump input for two ticks so a quick press is not lost because of packet ordering.
2. `EntityKnockbackEvent` and `PlayerVelocityEvent` grant three ticks of protection to combat knockback, explosions, fishing rods, and other server-sent velocity.
3. When Paper fires `PlayerJumpEvent`, the plugin requires all of the following:
   - Survival or Adventure mode;
   - no current or recent jump input;
   - no current or recent external velocity;
   - no flight, gliding, riptide, swimming, vehicle, or Levitation state;
   - movement is not a collision-supported step within the player's effective step-height attribute;
   - water contact with a waterlogged slab or stair beneath the lower player hitbox; and
   - horizontal movement input that points into a collision.
4. The first matching jump is cancelled and confirms suppression for that wall contact. The fallback correction keeps the newest collision-safe horizontal position and camera direction instead of returning X/Z to Paper's older movement-event location.
5. At each `ClientTickEndEvent` while the confirmed condition remains true, the plugin sends a self-only motion packet with Y set to zero. The vanilla client prepares the unwanted `+0.3Y` water-exit impulse for its next tick, so this normally clears the impulse before it moves the camera and retriggers another rollback.
6. Because the motion packet contains all three components, the plugin estimates X/Z from the player's latest client-tick displacement and reapplies vanilla's water damping. Diagonal movement along the wall is preserved rather than replaced with zero horizontal velocity.
7. If `prediction-distance` is positive, the same client-side suppression can start shortly before the first confirmed jump.

## Build locally

The checked-in Gradle wrapper downloads Gradle 9.4.1 and verifies its SHA-256 checksum. Install a Java 25 JDK, then run:

```bash
./gradlew clean build
```

The deployable file is written to:

```text
build/libs/WaterloggedJumpFix-x.x.x.jar
```

Unit tests run as part of `build` and of the GitHub Actions workflow.
