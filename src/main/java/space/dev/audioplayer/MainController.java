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
import java.util.prefs.Preferences;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

import javafx.animation.AnimationTimer;

public class MainController {

    @FXML
    private Button openButton, playButton, pauseButton, stopButton;

    @FXML
    private Slider volumeSlider, progressSlider;

    @FXML
    private Label currentTimeLabel, totalTimeLabel, volumeLabel ;

    @FXML
    private ListView<String> fileListView;

    private File currentDirectory;

    private Clip audioClip;
    private AudioInputStream audioStream;
    private DoubleProperty volume = new SimpleDoubleProperty(0.5);
    private boolean isDragging = false;

    private Preferences prefs;
    private static final String PREF_KEY_LAST_DIR = "lastOpenedDirectory";

    @FXML
    public void initialize() {
        playButton.setDisable(true);
        pauseButton.setDisable(true);
        stopButton.setDisable(true);

        volumeSlider.valueProperty().bindBidirectional(volume);
        volumeLabel.textProperty().bind(Bindings.format("Volume: %.0f%%", volumeSlider.valueProperty().multiply(100)));

        prefs = Preferences.userNodeForPackage(MainController.class);
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
        File[] audioFiles = directory.listFiles((dir, name) -> name.endsWith(".wav"));
        if (audioFiles != null) {
            fileListView.getItems().clear();
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
            stopButton.setDisable(false);
        }
    }

    @FXML
    private void handlePause() {
        if (audioClip != null && audioClip.isRunning()) {
            audioClip.stop();
            playButton.setDisable(false);
            pauseButton.setDisable(true);
        }
    }

    @FXML
    private void handleStop() {
        if (audioClip != null) {
            audioClip.stop();
            audioClip.setFramePosition(0);
            playButton.setDisable(false);
            pauseButton.setDisable(true);
            stopButton.setDisable(true);
            progressSlider.setValue(0);
            currentTimeLabel.setText("00:00");
        }
    }

    private void openAudioFile(File audioFile) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        if (audioClip != null) {
            audioClip.close();
        }
        if (audioStream != null) {
            audioStream.close();
        }

        try (AudioInputStream newStream = AudioSystem.getAudioInputStream(audioFile)) {
            audioStream = newStream;
            AudioFormat format = audioStream.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            audioClip = (Clip) AudioSystem.getLine(info);
            audioClip.open(audioStream);

            FloatControl volumeControl = (FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
            volumeControl.setValue((float) (Math.log(volume.get()) / Math.log(10) * 20));

            totalTimeLabel.setText(formatTime(audioClip.getFrameLength() / format.getFrameRate()));

            audioClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP && !audioClip.isRunning()) {
                    Platform.runLater(() -> handleStop());
                }
            });
        }
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