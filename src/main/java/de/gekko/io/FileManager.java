package de.gekko.io;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.image.Image;

/**
 *
 * @author --
 * 
 * Klasse zum Auslesen von Files außerhalb des Classpaths.
 *
 */
public class FileManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("FileManager");
	private static final Map<String, Path> MAP_FILES = new HashMap<>();
	private static final Map<String, Image> MAP_IMAGES = new HashMap<>();
	// Hier Pfad zu Files, die nicht auf dem Classpath liegen.
	private static final String PATH_FILES = "dir/...";
	// Hier Pfad zu Bildern, die nicht auf dem Classpath liegen.
	private static final String PATH_IMAGES = "dir/...";

	private static Set<String> availableFiles() {
		return new HashSet<String>(MAP_FILES.keySet());
	}

	public static List<Image> getAvatarImages() {
		return new ArrayList<>(MAP_IMAGES.values());
	}

	public static List<String> getDeckNames() {
		return new ArrayList<>(MAP_FILES.keySet());
	}

	public static Path getDeckPath(String deckName) throws NoSuchResourceException {
		final String fileName = deckName;
		if (availableFiles().contains(fileName)) {
			return MAP_FILES.get(fileName);
		} else {
			throw new NoSuchResourceException(fileName);
		}
	}

	public static void loadFiles() {
		mapFileDirectory(MAP_FILES, PATH_FILES);
	}

	public static void loadImages() {
		mapImageDirectory(MAP_IMAGES, PATH_IMAGES);
	}

	private static void mapFileDirectory(Map<String, Path> map, String directory) {
		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(directory))) {
			for (final Path path : directoryStream) {
				final String fileName = path.getFileName().toString();
				map.put(fileName, path);
				LOGGER.trace("Path added: {} -> {}", fileName, path.toString());
			}
		} catch (final IOException ex) {
			ex.printStackTrace();
		}
	}

	private static void mapImageDirectory(Map<String, Image> map, String directory) {
		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(directory))) {
			for (final Path path : directoryStream) {
				final String pathString = path.toFile().toURI().toString();
				map.put(path.getFileName().toString(), new Image(pathString));
				LOGGER.trace("Image added: {}", pathString);
			}
		} catch (final IOException ex) {
			ex.printStackTrace();
		}
	}
}
