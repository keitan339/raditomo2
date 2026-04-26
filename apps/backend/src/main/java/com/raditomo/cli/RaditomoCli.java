package com.raditomo.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "raditomo",
        mixinStandardHelpOptions = true,
        version = "raditomo 0.1.0",
        description = "Raditomo CLI",
        subcommands = {
                UsersCommand.class,
                DownloadCommands.DownloadCommand.class,
                DownloadCommands.DownloadProgramsCommand.class,
                DownloadCommands.DownloadAudioCommand.class
        }
)
public class RaditomoCli implements Runnable {
    @Override
    public void run() {
        // ルートコマンドのみ呼ばれた場合は help を促す
        System.out.println("Use --help to see available commands");
    }
}
