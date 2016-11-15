package com.rinke.solutions.pinball.ui;


import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;

import org.eclipse.swt.graphics.Image;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fappel.swt.DisplayHelper;
import com.rinke.solutions.pinball.animation.Animation;
import com.rinke.solutions.pinball.animation.AnimationFactory;
import com.rinke.solutions.pinball.animation.AnimationType;
import com.rinke.solutions.pinball.animation.CompiledAnimation;
import com.rinke.solutions.pinball.model.Palette;

public class GifExporterSWTTest {
	
	@Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

	@Rule
	public final DisplayHelper displayHelper = new DisplayHelper();

	private GifExporter gifExporter;
	
	protected Palette palette = Palette.getDefaultPalettes().get(0);

	@Before
	public void setUp() throws Exception {
		Animation ani = CompiledAnimation.buildAnimationFromFile("./src/test/resources/ex1.ani", AnimationType.COMPILED);
		gifExporter = new GifExporter(displayHelper.createShell(), palette, ani );
		gifExporter.createContents();
	}

	@Test
	public void testExportAni() throws Exception {
		String filename = testFolder.newFile("1.ani").getPath();
		gifExporter.exportAni(filename);
	}
	
	@Test
	public void testConvert() throws Exception {
		Image srcImage = new Image(displayHelper.getDisplay(), 200, 100);
		BufferedImage bufferedImage = gifExporter.convert(srcImage );
		assertEquals(200, bufferedImage.getWidth());
	}


}