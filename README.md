# Thingy

Discord bot to generate images based on a text prompt - way more than just that! Through a wide variety of tools, you can alter your generated images and share them with friends.

Integrates with DiscoArt, Stable Diffusion and other peculiarities, all rolled up in a single, sexy user interface that allows one to mix and match different configurations without a steeper learning curve!

- Powerful customization tools
- Fairness queue: Making sure everyone gets to make art!
- Social features: Sharing, profiles and stats
- Advanced Profile customization
- **Chapters**: Work on your pieces, and switch back to previous works of art!
  - Every "creation" command (`/stable_diffusion`, `/disco_diffusion`) results in a new chapter
  - Users can use variation commands (`/upscale`, `/variate`, ~~more to come~~) to alter said chapters as they wish
  - Don't like a change? Just do `/rollback`! There's unlimited undo!
  - Users can instantly swap back to previous chapters with `/chapters`
    
[Demo video + dev journey](https://www.youtube.com/watch?v=epLF0OXTp-A)

In short: this bot allows you to generate images based on a text prompt, but can do way more than just that - offering a wide variety of tools to make alter your generated images.

# Credits and special thanks

 - First and foremost Han Xiao for being around in DMs helping me with what I struggled with but also putting me on the right direction in various moments
 - [DiscoArt](https://github.com/jina-ai/discoart): without this project, I was never able to cook this up in a weekend
 - [Jina](https://jina.ai) which has some incredible tooling I got familiar with

# Demo

[My discord server](https://discord.gg/j4wQYhhvVd) has the bot running, both alpha and production bots are up!

# Run it yourself!

As this bot is open-source, anyone can run it. Depending on the method, you need different specs. The easiest is through Jina Cloud

## Installing via Jina Cloud

**Note!** Free for now! As we evolved from a weekend project to a larger-scale bot, things have changed a lot, and I thank you all. Please, if you use this in your own server, do get in touch with me, so we can see what works and what doesn't. Any feedback is really appreciated! Feel free to join [Thingy's birthplace](https://discord.gg/j4wQYhhvVd) or shoot a line in the projects Issues page!

What you need:
 - Linux or Windows with at least Java 18
 - Discord account and bot (more on this later)
 - A python environment to launch your Jina deployment

**Steps**:

 - Follow the tutorial to install JCloud: https://docs.jina.ai/fundamentals/jcloud
 - Create a directory where we will set up our bot
 - Download [the following](/flow_server.yml) file in the directory
 - In the directory, run `jc deploy flow_server.yml` to launch your own bot server! This may take a while
   - You should get a URL similar to `xxxxx.wolf.jina.ai`, make note of that URL as you need it in the bot configuration!
 - Download [this file](/config.example.yml) and name it `config.yml`, put it into the bot directory. 
 - Once done, follow "Setting up the bot" and then go back here
 - Download the bot interface (communicates with the AI) [from here](https://github.com/peterwilli/Thingy/releases/tag/v2-alpha-1) and put it in the bot directory
 - In a terminal in the bot directory, now type `java -jar DiscordArtBot-1.0-SNAPSHOT-all.jar`
 - In a bit, it should be up and running. Congrats, you can use `/stable_diffusion` and other peculiarities!

## Setting up the bot

- Create a Discord application on their [developer portal](https://discord.com/developers/applications/me)
- Make a discord bot by clicking on the Bot menu, and click "Add Bot". Name it as you like
- Click "Reset Token" to reveal the bot token. Make note of this token, you need it in the next step
- In the config.yml, change the bot name and token to your bot token
- Assuming you run the Jina Flow server as well as the Discord bot on the same machine, you don't need to do anything
    - If your Jina server is remote, you have to change `host` and `port` under `grpcServer`
    - If you run on JCLoud, make sure to set `port` to `443` and `plainText` to `false`!
- Save the config
- Go to the OAuth2 menu, and click the URL Generator submenu. Check off the Bot checkbox
- In bot permissions, you only need the following:
    ![Bot permissions checkboxes](./extras/bot_perms.png)
- After you have done this, you can copy the link and invite the bot into your server
  - The first time, the bot may be triggering a timeout error, this is because it has to download all the model files. After it's done, it'll run properly
- You can now resume the previous steps (wherever you were forwarded from)

### How to enable the Share feature (optional)

- [Make sure Discord is in developer mode](https://www.howtogeek.com/714348/how-to-enable-or-disable-developer-mode-on-discord)
- Make a channel, any place you like (bot needs to be able to post to it, so make sure has write permissions there). Name it something like `gallery`
- Right-click on the channel and click "Copy ID"
- Paste the channel ID in `shareChannelID` in the bot's `config.yml`. Restart bot if its running!
- Now you and your members can use `/share` for showcasing your fine art!

# Sister projects

These are co-developed with Thingy!

- [Diffusers executor](https://github.com/peterwilli/DiffusersExecutor)