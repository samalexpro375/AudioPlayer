package space.dev.audioplayer;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.stage.DirectoryChooser;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.prefs.Preferences;

import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;

import javafx.animation.AnimationTimer;

import static java.lang.Float.NaN;

public class MainController {

    @FXML
    private Button openButton, playButton, pauseButton, cycleButton;

    @FXML
    private Slider volumeSlider, progressSlider;

    @FXML
    private Label currentTimeLabel, totalTimeLabel, volumeLabel;

    @FXML
    private ListView<String> fileListView;


    private boolean isCycleMode = false;
    private File currentDirectory;
    private Clip audioClip;
    private AudioInputStream audioStream;
    private DoubleProperty volume;
    private boolean isDragging = false;

    private Preferences prefs;
    private static final String PREF_KEY_LAST_DIR = "lastOpenedDirectory";
    private static final String PREF_KEY_LAST_VOLUME = "lastVolume";


    @FXML
    public void initialize() {

        prefs = Preferences.userNodeForPackage(MainController.class);

        playButton.setDisable(true);
        pauseButton.setDisable(true);
        cycleButton.setDisable(true);

        volume = new SimpleDoubleProperty(Double.parseDouble(prefs.get(PREF_KEY_LAST_VOLUME, String.valueOf(0.5))));
        volumeSlider.valueProperty().bindBidirectional(volume);
        volumeLabel.textProperty().bind(Bindings.format("Volume: %.0f%%", volumeSlider.valueProperty().multiply(100)));


        String lastDirPath = prefs.get(PREF_KEY_LAST_DIR, null);

        if (lastDirPath != null) {
            File lastDir = new File(lastDirPath);
            if (lastDir.exists() && lastDir.isDirectory()) {
                currentDirectory = lastDir;
                loadAudioFiles(currentDirectory);
            }
        }

        volume.addListener((obs, oldVal, newVal) -> {
            if (audioClip != null) {
                FloatControl volumeControl = (FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) (Math.log(newVal.doubleValue()) / Math.log(10) * 20);
                volumeControl.setValue(dB);
            }
            // Save the last volume value
            prefs.put(PREF_KEY_LAST_VOLUME, String.valueOf(newVal.doubleValue()));
        });


        fileListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null && currentDirectory != null) {
                File selectedFile = new File(currentDirectory, newSelection);
                try {
                    Platform.runLater(() -> {
                        try {
                            openAudioFile(selectedFile);
                            playButton.setDisable(false);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (audioClip != null && !isDragging) {
                    int currentFrame = audioClip.getFramePosition();
                    int totalFrames = audioClip.getFrameLength();
                    double progress = (double) currentFrame / totalFrames;
                    progressSlider.setValue(progress * 100);
                    currentTimeLabel.setText(formatTime(currentFrame / audioClip.getFormat().getFrameRate()));
                }
            }
        };
        timer.start();

        progressSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            isDragging = isChanging;
            if (!isChanging && audioClip != null) {
                int totalFrames = audioClip.getFrameLength();
                int newFramePosition = (int) (progressSlider.getValue() / 100 * totalFrames);
                audioClip.setFramePosition(newFramePosition);
                currentTimeLabel.setText(formatTime(newFramePosition / audioClip.getFormat().getFrameRate()));
            }
        });
    }

    @FXML
    private void handleOpenFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        currentDirectory = directoryChooser.showDialog(openButton.getScene().getWindow());

        if (currentDirectory != null) {
            prefs.put(PREF_KEY_LAST_DIR, currentDirectory.getAbsolutePath());
            loadAudioFiles(currentDirectory);
        }
    }

    private void loadAudioFiles(File directory) {
        // Фильтруем файлы для отображения только WAV и MP3
        File[] audioFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".wav") || name.toLowerCase().endsWith(".mp3"));

        // Проверяем, что файлы найдены
        if (audioFiles != null) {
            // Очищаем список перед добавлением новых файлов
            fileListView.getItems().clear();

            // Добавляем все найденные аудиофайлы в список
            for (File file : audioFiles) {
                fileListView.getItems().add(file.getName());
            }
        }
    }

    @FXML
    private void handlePlay() {
        if (audioClip != null) {
            audioClip.start();
            playButton.setDisable(true);
            pauseButton.setDisable(false);
            cycleButton.setDisable(false);
        }
    }

    @FXML
    private void handlePause() {
        if (audioClip != null && audioClip.isRunning()) {
            audioClip.stop();  // Останавливаем, но не сбрасываем позицию
            playButton.setDisable(false);  // Включаем кнопку Play
            pauseButton.setDisable(true);  // Отключаем кнопку Pause
        }
    }

    @FXML
    private void handleStop() {
        if (audioClip != null) {
            audioClip.stop();
            audioClip.setFramePosition(0);
            playButton.setDisable(false);
            pauseButton.setDisable(true);
            cycleButton.setDisable(true);
            progressSlider.setValue(0);
            currentTimeLabel.setText("00:00");
        }
    }

    @FXML
    private void handleCycle() {
        isCycleMode = !isCycleMode;
        cycleButton.setStyle(isCycleMode ? "-fx-background-color: #4fbcff;" : "-fx-background-color: #e0e0e0;");
    }

    private void openAudioFile(File audioFile) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        if (audioClip != null) {
            audioClip.close();
        }
        if (audioStream != null) {
            audioStream.close();
        }

        audioStream = AudioSystem.getAudioInputStream(audioFile);
        AudioFormat baseFormat = audioStream.getFormat();
        AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
                false
        );

        AudioInputStream decodedAudioStream = AudioSystem.getAudioInputStream(decodedFormat, audioStream);

        DataLine.Info info = new DataLine.Info(Clip.class, decodedFormat);
        audioClip = (Clip) AudioSystem.getLine(info);
        audioClip.open(decodedAudioStream);

        FloatControl volumeControl = (FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
        volumeControl.setValue((float) (Math.log(volume.get()) / Math.log(10) * 20));

        totalTimeLabel.setText(formatTime(audioClip.getFrameLength() / decodedFormat.getFrameRate()));

        audioClip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP && audioClip.getFramePosition() == audioClip.getFrameLength()) {
                if (isCycleMode) {
                    Platform.runLater(this::handleStop);
                    Platform.runLater(this::handlePlay);
                } else {
                    // Если режим воспроизведения не циклический, остановите воспроизведение музыки
                    Platform.runLater(this::handleStop);
                }
            }
        });
    }


    private String formatTime(double seconds) {
        int minutes = (int) seconds / 60;
        int secs = (int) seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    public void stop() throws Exception {
        if (audioClip != null) {
            audioClip.close();
        }
        if (audioStream != null) {
            audioStream.close();
        }
    }
}