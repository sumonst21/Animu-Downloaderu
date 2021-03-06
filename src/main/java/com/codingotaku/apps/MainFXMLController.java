package com.codingotaku.apps;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

import com.codingotaku.apps.callback.Crawler;
import com.codingotaku.apps.callback.TableObserver;
import com.codingotaku.apps.callback.TableSelectListener;
import com.codingotaku.apps.custom.AnimeLabel;
import com.codingotaku.apps.custom.DownloadDialog;
import com.codingotaku.apps.custom.EpisodeLabel;
import com.codingotaku.apps.custom.LoadDialog;
import com.codingotaku.apps.download.DownloadInfo;
import com.codingotaku.apps.download.DownloadManager;
import com.codingotaku.apps.download.Status;
import com.codingotaku.apps.source.Servers;
import com.codingotaku.apps.source.Source;
import com.codingotaku.apps.util.Backup;
import com.codingotaku.apps.util.Constants;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

public class MainFXMLController implements TableObserver, Crawler {
	@FXML
	private VBox root; // Root

	// Title bar
	@FXML
	private Label minimize;
	@FXML
	private Label resize;
	@FXML
	private Label close;
	@FXML
	private HBox title;
	@FXML
	private HBox center;

	// Anime download and interactions
	@FXML
	private TextField search;
	@FXML
	private CheckBox cb;
	@FXML
	private ComboBox<String> sources;
	@FXML
	private Button download;

	// Anime information
	@FXML
	private Button showEpisodes;
	@FXML
	private ImageView poster;
	@FXML
	private WebView webView;

	// For displaying downloads
	@FXML
	private ScrollPane scrollPane;
	@FXML
	private TableView<DownloadInfo> tableView;
	@FXML
	private TableColumn<DownloadInfo, String> fileName;
	@FXML
	private TableColumn<DownloadInfo, Double> size;
	@FXML
	private TableColumn<DownloadInfo, Double> downloaded;
	@FXML
	private TableColumn<DownloadInfo, String> progress;
	@FXML
	private TableColumn<DownloadInfo, Status> status;

	private final ObservableList<EpisodeLabel> episodes = FXCollections.observableArrayList();
	private final ObservableList<AnimeLabel> animes = FXCollections.observableArrayList();
	private final DownloadManager manager = DownloadManager.getInstance();

	private ListView<EpisodeLabel> episodeList;
	private ListView<AnimeLabel> animeList;

	private final Delta dragDelta = new Delta();// for title bar dragging

	private WebEngine webEngine;
	private Servers servers;
	private Window window;
	private Stage stage;
	private VBox vBox;

	private Image defaultImg;
	
	@FXML private ImageView boxImage;

	@FXML
	private void showEpisodes(ActionEvent event) {
		if (showEpisodes.getText().equals("Show Episodes")) {
			loadEpisodes();
			download.setDisable(false);
			showEpisodes.setText("Back to Anime list");
		} else {
			webEngine.loadContent("<html><body bgcolor='#424242'></body></html>");
			showEpisodes.setDisable(true);
			loadAnime(window);
			poster.setImage(defaultImg);
			showEpisodes.setText("Show Episodes");
		}
	}

	private void loadEpisodes() {
		cb.setIndeterminate(false);
		cb.setSelected(false);
		episodes.setAll(servers.loadEpisodes());
		episodeList = new ListView<EpisodeLabel>();
		episodeList.setItems(episodes);
		episodeList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		episodeList.setOnMouseClicked(event -> {
			int size = episodeList.getSelectionModel().getSelectedIndices().size();
			if (size == 0) {
				cb.setIndeterminate(false);
				cb.setSelected(false);
			}
			if (size < episodes.size()) {
				cb.setIndeterminate(true);
			} else {
				cb.setIndeterminate(false);
				cb.setSelected(true);
			}
		});
		VBox.setVgrow(episodeList, Priority.ALWAYS);
		vBox.getChildren().setAll(episodeList);
	}

	@FXML
	private void chooseFolder() {
		if (window == null)
			window = showEpisodes.getScene().getWindow();
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle("Select Download folder");
		File defaultDirectory;

		defaultDirectory = new File(Constants.downloadFolder);
		if (!defaultDirectory.exists()) {// If the path was external HD or it doesn't exist.
			String defaultPath = System.getProperty("user.home") + "\\Downloads";
			Constants.downloadFolder = defaultPath.replace("\\", "/");
			defaultDirectory = new File(Constants.downloadFolder);
		}

		chooser.setInitialDirectory(defaultDirectory);
		File selectedDir = chooser.showDialog(window);
		if (selectedDir != null && selectedDir.exists()) {
			Constants.downloadFolder = selectedDir.getAbsolutePath().replace("\\", "/");
			Backup.saveDownloadFolder();
		}
	}

	@FXML
	private void download(ActionEvent event) {
		int count = episodeList.getSelectionModel().getSelectedItems().size();
		Optional<Boolean> result = new DownloadDialog(count).showAndWait();
		result.ifPresent(res -> {
			if (res) downloadSelected();
		});
	}

	void loadAnime(Window window) {
		if (this.window == null) {
			this.window = window;
		}
		servers.setSource(Source.values()[sources.getSelectionModel().getSelectedIndex()]);
		animeList.getSelectionModel().clearSelection();
		new Thread(() -> {
			List<AnimeLabel> list = servers.loadAnime(window);
			if (list != null) {
				Platform.runLater(() -> {
					animes.setAll(list);
					search(search.getText());
					VBox.setVgrow(animeList, Priority.ALWAYS);
					vBox.getChildren().setAll(animeList);
				});
			}
		}).start();
	}

	@FXML
	private void initialize() {
		webEngine = webView.getEngine();
		webEngine.loadContent("<html><body bgcolor='#424242'></body></html>");
		vBox = (VBox) scrollPane.getContent().lookup("#list");

		fileName.setCellValueFactory(new PropertyValueFactory<DownloadInfo, String>("fileName"));
		size.setCellValueFactory(new PropertyValueFactory<DownloadInfo, Double>("size"));
		downloaded.setCellValueFactory(new PropertyValueFactory<DownloadInfo, Double>("downloaded"));
		progress.setCellValueFactory(new PropertyValueFactory<DownloadInfo, String>("progress"));
		status.setCellValueFactory(new PropertyValueFactory<DownloadInfo, Status>("status"));

		tableView.setRowFactory(new TableSelectListener());
		search.textProperty().addListener((observable, oldValue, newValue) -> search(newValue));
		servers = Servers.getInstance(this);
		manager.setController(this);
		sources.getSelectionModel().select(0);
		defaultImg = new Image(getClass().getResourceAsStream("/icons/panda.png"));
		poster.setImage(defaultImg);
		sources.valueProperty().addListener(e -> {
			loadAnime(window);
			poster.setImage(defaultImg);
			webEngine.loadContent("<html><body bgcolor='#424242'></body></html>");
		});

		cb.selectedProperty().addListener((paramObservableValue, old, flag) -> {
			if (episodeList == null)
				return;
			if (flag)
				episodeList.getSelectionModel().selectAll();
			else
				episodeList.getSelectionModel().clearSelection();
		});

		animeList = new ListView<>();
		animeList.getSelectionModel().selectedItemProperty().addListener((observable, oldV, newV) -> {
			if (newV != null)
				servers.getSynopsys(newV);
		});

	}

	private void search(String text) {
		showEpisodes.setDisable(true);
		download.setDisable(true);
		animeList.getSelectionModel().clearSelection();
		if (!(animes.isEmpty())) {
			if (text.isEmpty()) {
				animeList.setItems(animes);
			} else {
				animeList.setItems(animes.filtered(label -> label.hasValue(text)));
			}
		}
		if (!vBox.getChildren().contains(animeList)) {
			vBox.getChildren().setAll(animeList);
		}
	}

	private void downloadSelected() {
		new Thread(() -> {
			episodeList.getSelectionModel().getSelectedItems()
					.forEach(episode -> manager.addDownloadURL(episode.copy()));
		}).start();
	}

	@Override
	public void added(DownloadInfo download) {
		tableView.getItems().add(download);
	}

	@Override
	public void updated(DownloadInfo download) {
		tableView.refresh();
	}

	@Override
	public void loading() {
		LoadDialog.showDialog(window, "Please wait", "Fetching anime details");
	}

	@Override
	public void loaded(String content) {
		Platform.runLater(() -> {
			webEngine.loadContent(content);
			LoadDialog.stopDialog();
			showEpisodes.setDisable(false);
			showEpisodes.setText("Show Episodes");
		});
	}

	@Override
	public void poster(Image image) {
		Platform.runLater(() -> poster.setImage(image));
	}

	/*
	 * Some times the JavaFX unMaximize/maximize does not work (in Some OS in a few
	 * versions This hopefully takes care of that!
	 * 
	 */
	void unMaximize(Stage primaryStage) {
		Preferences preferences = Preferences.userNodeForPackage(Main.class);
		double x = preferences.getDouble("x", 0);
		double y = preferences.getDouble("y", 0);
		double w = preferences.getDouble("w", 0);
		double h = preferences.getDouble("h", 0);

		primaryStage.setX(x);
		primaryStage.setY(y);
		primaryStage.setWidth(w);
		primaryStage.setHeight(h);
	}

	/*
	 * Some times the JavaFX unMaximize/maximize does not work (in Some OS in a few
	 * versions This hopefully takes care of that!
	 * 
	 */
	void maximize(Stage primaryStage) {
		Preferences preferences = Preferences.userNodeForPackage(Main.class);
		preferences.putDouble("x", stage.getX());
		preferences.putDouble("y", stage.getY());
		preferences.putDouble("w", stage.getWidth());
		preferences.putDouble("h", stage.getHeight());

		Rectangle2D rect = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
		// For multiple monitors, get the monitor that can handle the app size, if not
		// the app will crash!
		// I dont't care who you are, but if you cannot afford a good monitor then you
		// shouldn't be watching anime all day
		ObservableList<Screen> screens = Screen.getScreensForRectangle(rect);

		Rectangle2D bounds = screens.get(0).getVisualBounds();
		primaryStage.setX(bounds.getMinX());
		primaryStage.setY(bounds.getMinY());
		primaryStage.setWidth(bounds.getWidth());
		primaryStage.setHeight(bounds.getHeight());
	}

	@FXML
	private void resize() {

		if (stage == null)
			stage = (Stage) close.getScene().getWindow(); // an ugly way of initializing stage
		if (stage.isMaximized()) {
			stage.setMaximized(false);
			unMaximize(stage);
			resize.setText("◻");
		} else {
			stage.setMaximized(true);
			maximize(stage);
			resize.setText("⧉");
		}
	}

	@FXML
	private void titleSelected(MouseEvent event) {
		if (stage == null)
			stage = (Stage) title.getScene().getWindow();
		dragDelta.x = event.getScreenX() - stage.getX();
		dragDelta.y = event.getScreenY() - stage.getY();
	}

	@FXML
	private void titleDragged(MouseEvent event) {
		if (stage.isMaximized()) {
			double pw = stage.getWidth();
			resize();
			double nw = stage.getWidth();
			dragDelta.x /= (pw / nw);
		}
		stage.setX(event.getScreenX() - dragDelta.x);
		stage.setY(event.getScreenY() - dragDelta.y);
	}

	@FXML
	private void titleReleased(MouseEvent event) {
		if (event.getScreenY() == 0 && !stage.isMaximized()) {
			resize();
		}
	}

	@FXML
	private void minimize() {
		if (stage == null)
			stage = (Stage) title.getScene().getWindow();
		stage.setIconified(true);
	}

	@FXML
	private void close() {
		if (stage == null)
			stage = (Stage) title.getScene().getWindow();
		ConfirmDialog dialog = new ConfirmDialog("Exit?", Constants.EXIT_QUESTION);
		Optional<Boolean> res = dialog.showAndWait();
		if (res.isPresent()) {
			if (res.get()) {
				DownloadManager.getInstance().pauseAll();
				stage.close();
				System.exit(0);// I shouldn't do this but for now I'll force close the app.
			}
		}
	}

	private class Delta {
		double x, y;
	}
}