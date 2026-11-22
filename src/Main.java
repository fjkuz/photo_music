import java.awt.Desktop;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.util.Scanner;

private static final String INPUT_FILE = "input.txt";
void main() {
    Scanner scanner = new Scanner(System.in);
    //выбор фото
    System.out.println("Выберите изображение:");
    System.out.println(" 1 - кролик");
    System.out.println(" 2 - аура");
    System.out.println(" 3 - кошка за компьютером");

    int imageChoice = readInt(scanner);
    String imageUrl;
    String imagePath;

    switch (imageChoice) {
        case 1:
            imageUrl = "https://i.pinimg.com/736x/ab/59/34/ab59349bf7ee7cf5b6c725d5fdb7c9d4.jpg";
            imagePath = "images/rabbit.jpg";
            break;
        case 2:
            imageUrl = "https://i.pinimg.com/736x/77/64/ca/7764ca368abc944b27d17991463ee264.jpg";
            imagePath = "images/aura.jpg";
            break;
        default:
            imageUrl = "https://i.pinimg.com/736x/f5/eb/b6/f5ebb633187a9363bd72e0393ae2f682.jpg";
            imagePath = "images/cat.jpg";
            break;
    }
    //выбор музыки
    System.out.println("\nВыберите песню:");
    System.out.println(" 1 пятый элемент");
    System.out.println(" 2 семнадцатилетняя");
    System.out.println(" 3 они все *** на тебя в интернете");

    int musicChoice = readInt(scanner);
    String musicUrl;
    String musicPath;

    switch (musicChoice) {
        case 1:
            musicUrl = "https://ru-d3.drivemusic.me/dl/cQ2dNdbXatfOuuhyeEFxfg/1794181953/download_music/2023/10/cupsize-pjatyjj-jelement.mp3";
            musicPath = "music/pjatyjj-jelement.mp3";
            break;
        case 2:
            musicUrl = "https://ru-d1.drivemusic.me/dl/PlfGsq4381wEusjFj4m8wA/1794182050/download_music/2023/12/cupsize-semnadcatiletnjaja.mp3";
            musicPath = "music/semnadcatiletnjaja.mp3";
            break;
        default:
            musicUrl = "https://ru-d1.drivemusic.me/dl/GNVMI1QGA4bduUUEdNSaQA/1794182282/download_music/2023/12/cupsize-oni-vse-drochat-na-tebja-v-internete.mp3";
            musicPath = "music/oni-vse-na-tebja-v-internete.mp3";
            break;
    }
    // Запись ссылок и путей в файл
    try (FileWriter writer = new FileWriter(INPUT_FILE)) {
        writer.write(imageUrl + " " + imagePath + "\n");
        writer.write(musicUrl + " " + musicPath + "\n");
    }
    catch (IOException e) {
        handleError("Ошибка записи файла", e);
        return;
    }
    //чтение данных из файла
    try (Scanner fileScanner = new Scanner(new File(INPUT_FILE))) {

        String imageLine = fileScanner.nextLine();
        String musicLine = fileScanner.nextLine();
        String[] imageParts = imageLine.split("\\s+");
        String[] musicParts = musicLine.split("\\s+");

        //поток для скачивания фото
        Thread downloadImageThread = new Thread(() -> {
            try {
                downloadUsingNIO(imageParts[0], imageParts[1]);
                openFile(imageParts[1]);
            }
            catch (IOException e) {
                handleError("ошибка скачивания изображения", e);
            }
        });
        //поток для скачивания музыки
        Thread downloadMusicThread = new Thread(() -> {
            try {
                downloadUsingNIO(musicParts[0], musicParts[1]);
                openFile(musicParts[1]);
            }
            catch (IOException e) {
                handleError("ошибка скачивания музыки", e);
            }
        });
        //одновременный запуск потоков
        downloadImageThread.start();
        downloadMusicThread.start();
        //ожидание завершения потоков
        try {
            downloadImageThread.join();
            downloadMusicThread.join();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Ожидание потоков прервано");
        }
    }
    catch (IOException e) {
        handleError("Ошибка чтения файла", e);
    }
    scanner.close();
}
//проверка введённого числа
private static int readInt(Scanner scanner) {
    while (true) {
        try {
            int choice = Integer.parseInt(scanner.nextLine());
            if (choice >= 1 && choice <= 3) {
                return choice;
            }
            System.out.print("Введите число от " + 1 + " до " + 3 + ": ");
        }
        catch (NumberFormatException e) {
            System.out.print("Введите ЧИСЛО от " + 1 + " до " + 3 + ": ");
        }
    }
}
//скачивание файла с повторными попытками
private static void downloadUsingNIO(String url, String file) throws IOException {
    IOException lastError = null;

    for (int attempt = 1; attempt <= 3; attempt++) {
        try {
            downloadOnce(url, file);
            return;
        }
        catch (IOException e) {
            lastError = e;
            System.out.println("Попытка " + attempt + " не удалась: " + e.getMessage());
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Скачивание прервано", ex);
            }
        }
    }
    throw lastError;
}
//загрузка файла через NIO
private static void downloadOnce(String url, String file) throws IOException {
    Path path = Paths.get(file);
    Files.createDirectories(path.getParent());

    HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
    connection.setConnectTimeout(20000);
    connection.setReadTimeout(120000);
    connection.setInstanceFollowRedirects(true);

    Path temporaryFile = Paths.get(file + ".part");
    try {
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status);
        }
        String contentType = connection.getContentType();
        if (contentType != null &&
                contentType.toLowerCase().startsWith("text/html")) {
            throw new IOException("Сервер вернул HTML вместо файла");
        }
        try (
                ReadableByteChannel input =
                        Channels.newChannel(connection.getInputStream());
                FileOutputStream output =
                        new FileOutputStream(temporaryFile.toFile())
        ) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (input.read(buffer) != -1) {
                buffer.flip();
                output.getChannel().write(buffer);
                buffer.clear();
            }
        }
        Files.move(
                temporaryFile,
                path,
                StandardCopyOption.REPLACE_EXISTING
        );
        System.out.println("Файл успешно скачан: " + file);
    }
    finally {
        connection.disconnect();
        Files.deleteIfExists(temporaryFile);
    }
}
//открытие файла стандартной программой
private static void openFile(String filePath) {
    try {
        Desktop.getDesktop().open(new File(filePath));
        System.out.println("Файл открыт: " + filePath);
    }
    catch (IOException e) {
        System.out.println("Не удалось открыть файл: " + filePath);
        e.printStackTrace();
    }
}
//вывод информации об ошибке
private static void handleError(String message, Exception e) {
    System.err.println(message + ": " + e.getMessage());
}
