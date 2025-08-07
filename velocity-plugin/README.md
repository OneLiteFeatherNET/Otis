# Otis Velocity Plugin

This plugin integrates the Otis player management system with Velocity proxy servers.

## Features

- Automatically creates player records when players join for the first time
- Updates player data when players join or leave the server
- Configurable API endpoint

## Configuration

The plugin creates a configuration file at `plugins/otis/config.json` with the following structure:

```json
{
  "baseUrl": "http://localhost:8080"
}
```

### Configuration Options

- `baseUrl`: The base URL of the Otis API server (default: `http://localhost:8080`)

## How It Works

### Player Creation

When a player joins the server for the first time, the plugin:
1. Checks if the player exists in the Otis database
2. If not, creates a new player record with:
   - UUID
   - Player name
   - First join timestamp
   - Last join timestamp
   - Empty profile textures map

### Player Updates

When a player joins or leaves the server, the plugin:
1. Checks if the player exists in the Otis database
2. If found, updates the player record with:
   - Current player name
   - Updated last join timestamp
   - Preserves existing profile textures and first join timestamp

## Development

### Project Structure

- `OtisPlugin.java`: Main plugin class that initializes the configuration and client
- `config/OtisConfig.java`: Configuration class for storing and managing the base URL
- `listener/PlayerListener.java`: Event listener for player join and leave events

### Dependencies

- Otis Client: Used to communicate with the Otis API
- Velocity API: Used to interact with the Velocity proxy server

## Building

To build the plugin, run:

```bash
./gradlew build
```

The plugin JAR will be created in `build/libs/`.