package au.edu.melbuni.boldapp.test;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import au.edu.melbuni.boldapp.models.Timeline;
import au.edu.melbuni.boldapp.models.Timelines;
import au.edu.melbuni.boldapp.models.User;
import au.edu.melbuni.boldapp.models.Users;
import au.edu.melbuni.boldapp.persisters.JSONPersister;
import au.edu.melbuni.boldapp.persisters.Persister;

@RunWith(CustomTestRunner.class)
public class PersisterTest {
	
	Persister persister;
	
	@Before
	public void setUp() throws Exception {
		persister = new JSONPersister();
	}
	
	@Test
	public void pathForWithUsers() {
		assertEquals("/mnt/sdcard/bold/users/list.json", persister.pathFor(new Users(new ArrayList<User>())));
	}
	
	@Test
	public void pathForWithUser() {
		assertEquals(
		  "/mnt/sdcard/bold/users/00000000-0000-0001-0000-000000000002.json",
		  persister.pathFor(new User(new UUID(1, 2)))
		);
	}
	
	@Test
	public void pathForWithTimelines() {
		assertEquals(
		  "/mnt/sdcard/bold/timelines/list.json",
		  persister.pathFor(new Timelines())
		);
	}
	
	@Test
	public void pathForWithTimeline() {
		assertEquals(
		  "/mnt/sdcard/bold/timelines/identifier.json",
		  persister.pathFor(new Timeline(null, "identifier"))
		);
	}
	
	@Test
	public void pathForUsers() {
		assertEquals("/mnt/sdcard/bold/users/list.json", persister.pathForUsers());
	}
	
	@Test
	public void pathForUser() {
		assertEquals("/mnt/sdcard/bold/users/identifier.json", persister.pathForUser("identifier"));
	}

	@Test
	public void pathForTimelines() {
		assertEquals("/mnt/sdcard/bold/timelines/list.json", persister.pathForTimelines());
	}
	
	@Test
	public void pathForTimeline() {
		assertEquals("/mnt/sdcard/bold/timelines/identifier.json", persister.pathForTimeline("identifier"));
	}
}
