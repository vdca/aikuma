package au.edu.melbuni.boldapp.models;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Observable;

import android.util.Log;
import au.edu.melbuni.boldapp.Player;
import au.edu.melbuni.boldapp.Recorder;
import au.edu.melbuni.boldapp.Sounder;
import au.edu.melbuni.boldapp.listeners.OnCompletionListener;
import au.edu.melbuni.boldapp.persisters.JSONPersister;
import au.edu.melbuni.boldapp.persisters.Persister;

public class Segment extends Observable {

	Timeline timeline = null;
	String identifier = null;

	protected boolean selected = false;
	protected boolean playing = false;
	protected boolean recording = false;

	public Segment(String identifier) {
		this.identifier = identifier;
	}

	public String getIdentifier() {
		return identifier;
	}

	public static Segment fromHash(Map<String, Object> hash) {
		String identifier = hash.get("id") == null ? "" : (String) hash
				.get("id");
		return new Segment(identifier);
	}

	public Map<String, Object> toHash() {
		Map<String, Object> hash = new LinkedHashMap<String, Object>();
		hash.put("id", this.identifier);
		return hash;
	}

	// Load a segment based on its identifier.
	//
	public static Segment load(Persister persister, String timelineIdentifier,
			String identifier) {
		return persister.loadSegment(timelineIdentifier, identifier);
	}

	// TODO Work on this one!
	// TODO Also unify the sound file name in one method.
	//
	public String getSoundfilePath(String timelineIdentifier) {
		Persister persister = new JSONPersister();
		return Sounder.generateFilePath(persister.dirForSegments(timelineIdentifier) + getIdentifier());
	}
	
	// TODO Remove.
	//
	public void putSoundfile(String timelineIdentifier, byte[] bytes) {
		String path = getSoundfilePath(timelineIdentifier); // TODO Refactor!

		BufferedOutputStream bufOut = null;
		try {
			// TODO DRY. See above.
			//
			File file = new File(path);
			file.getParentFile().mkdirs();
			file.createNewFile();

			FileOutputStream out = new FileOutputStream(path);
			bufOut = new BufferedOutputStream(out);

			bufOut.write(bytes);

			bufOut.close();
		} catch (Exception e) {
			System.out.println("ERROR:" + path);
			Log.e("Error reading file", e.toString());
		}
	}

	// Save the segment's metadata.
	//
	public void save(Persister persister, String timelineIdentifier) {
		persister.save(timelineIdentifier, this);
	}

	public boolean isPlaying() {
		return this.playing;
	}

	public void setRecording(boolean recording) {
		this.recording = recording;
		setChanged();
		notifyObservers();
	}

	public boolean isRecording() {
		return this.recording;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
		setChanged();
		notifyObservers();
	}

	public boolean isSelected() {
		return this.selected;
	}

	public void startPlaying(Player player, String directory,
			OnCompletionListener listener) {
		player.startPlaying(directory + identifier, listener);
		setPlaying(true);
	}

	public void stopPlaying(Player player) {
		player.stopPlaying();
		setPlaying(false);
	}

	public void startRecording(Recorder recorder, String directory) {
		recorder.startRecording(directory + identifier);
		setRecording(true);
	}

	public void stopRecording(Recorder recorder) {
		recorder.stopRecording();
		setRecording(false);
	}

	public void select() {
		setSelected(true);
	}

	public void deselect() {
		setSelected(false);
	}

	public void setPlaying(boolean playing) {
		this.playing = playing;
		setChanged();
		notifyObservers();
	}

	@Override
	public boolean equals(Object object) {
		if (object instanceof Segment) {
			Segment other = (Segment) object;
			return getIdentifier().equals(other.getIdentifier());
		}
		return false;
	}
	
//	public static class ViewHandler implements Observer {
//		
//		private View view;
//		
//		public ViewHandler(View view) {
//			this.view = view;
//		}
//		
//		@Override
//		public void update(Observable observable, Object data) {
//			Segment segment = (Segment) observable;
//			
//			if (segment.isRecording()) {
//				view.getBackground().setColorFilter(Color.RED, Mode.MULTIPLY);
//				return;
//			}
//			if (segment.isPlaying()) {
//				view.getBackground().setColorFilter(Color.GREEN, Mode.MULTIPLY);
//				return;
//			}
//			if (segment.isSelected()) {
//				view.getBackground().clearColorFilter();
//			} else {
//				view.getBackground().setColorFilter(Color.GRAY, Mode.MULTIPLY);
//			}
//		}
//		
//	}

}