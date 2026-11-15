# ReComputerCraft
Refined version of ComputerCraft v1.33 focused on increased performance and fewer bugs. Optimized for Tekkit Classic 3.1.2. For server use only.

All credits to Dan200 for the original ComputerCraft mod.

Changes
-

- Implements thread grouping to eliminate danging threads after terminating problematic computers.
- Implements a computer directory size limiter to prevent malicious disk usage.
- Implements a configurable input rate limiter to prevent or dissuade users from using auto-typer tools or abusing server resources.
- Implements a configurable computer thread task queue size option.
- Adds MLProp info to each MLProp.
- Renames ComputerCraft-related threads to be prefixed by `ReCC-`.
- Includes computer IDs in thread names and console error messages where possible to aid in debugging.