package io.vertx.example.eventbus.pubsub;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.example.eventbus.util.Runner;

public class FileReaderVerticle extends AbstractVerticle {

    // Convenience method so you can run it in your IDE
    public static void main(String[] args) {
        Runner.runClusteredExample(FileReaderVerticle.class);
    }

    @Override
    public void start() {
        EventBus eb = vertx.eventBus();
        FileSystem vertxFileSystem = vertx.fileSystem();

        vertxFileSystem.open("names.txt", new OpenOptions(), readFile -> {
            if (readFile.succeeded()) {
                AsyncFile file = readFile.result();
                file.handler(r -> RecordParser.newDelimited("\n", bufferedLine -> {
                            eb.publish("news-feed", bufferedLine);
                        }).exceptionHandler(Throwable::printStackTrace)
                                .handle(r)
                ).endHandler(v -> {
                    file.close();
                    System.out.println("Done");
                }).exceptionHandler(Throwable::printStackTrace);
            } else {
                System.out.println("Failure: " + readFile.cause().getMessage());
            }

        });
    }
}