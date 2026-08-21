# WaterloggedJumpFix

WaterloggedJumpFix is a small, runtime-dependency-free Paper plugin that works around [MC-8959](https://bugs.mojang.com/browse/MC-8959): players can receive an automatic upward boost when moving against a block in shallow water. It also covers the waterlogged slab and stair reproduction reported as [MC-174654](https://bugs.mojang.com/browse/MC-174654), including the case where Auto-Jump is disabled and the obstacle is too tall to climb.

## Requirements

- Paper 26.1.2
- Java 25 or newer

## Install

1. Download `WaterloggedJumpFix-x.x.x.jar` from the [latest GitHub Release](https://github.com/the-great-chicken/WaterloggedJumpFix/releases/latest), or build it locally as described below.
2. Put the JAR in the server's `plugins/` directory.
3. Fully restart the server. Avoid `/reload` and plugin hot-reloaders.
4. Confirm that the console logs `MC-8959 workaround enabled globally`.

The plugin has no commands, permissions, runtime dependencies, or per-world restrictions.

## Configuration

The generated `plugins/WaterloggedJumpFix/config.yml` contains:

```yaml
prediction-distance: 0.1
time-lookahead-max-distance: 0.6
```

The plugin sweeps at least `prediction-distance` blocks ahead, then adds the player's recent horizontal distance per client tick multiplied by their ping in 50 ms ticks. `time-lookahead-max-distance` caps the result. This starts suppression early enough for delayed motion packets without making low-latency players use the same oversized fixed distance. Both values accept up to `2.0` blocks, and `prediction-distance` may be `0.0`.

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
   - the player's body is in water while their eyes remain above the surface; and
   - horizontal movement input that points into a collision.
4. A swept collision probe uses the latency-adjusted distance to begin client-side suppression before contact. A separate step simulation recognizes reachable ledges, including a step encountered while sliding past an adjacent taller wall, and briefly permits that transition.
5. After a jump confirms the bug, a short contact and airborne-recovery window covers packet-ordering gaps without keeping suppression active after the player changes direction.
6. Matching movement receives a self-only motion packet with Y set to zero. The vanilla client prepares the unwanted `+0.3Y` water-exit impulse for its next tick, so this normally clears the impulse before it moves the camera and retriggers another rollback.
7. Because the motion packet contains all three components, the plugin reconstructs X/Z from the latest client-tick displacement, reapplies vanilla's water damping, and removes only stale components that oppose current input. Diagonal movement along the wall is preserved.
8. If an unwanted jump still reaches Paper, it is cancelled using the newest collision-safe horizontal position and immediately followed by another motion reset.

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

## Publish a release

Set `pluginVersion` in `gradle.properties`, commit the change, and push a matching tag such as `v1.4.0`. The GitHub Actions workflow validates the tag, runs the tests, builds the versioned JAR, and attaches it to a GitHub Release. Re-running the workflow replaces the existing JAR asset.
