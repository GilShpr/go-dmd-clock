package com.rinke.solutions.pinball;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import com.rinke.solutions.pinball.io.DMCImporter;
import com.rinke.solutions.pinball.io.FileHelper;
import com.rinke.solutions.pinball.io.PaletteImporter;
import com.rinke.solutions.pinball.io.SmartDMDImporter;
import com.rinke.solutions.pinball.model.Palette;
import com.rinke.solutions.pinball.model.RGB;
import com.rinke.solutions.pinball.util.FileChooserUtil;

@Slf4j
public class PaletteHandler {
	
	FileHelper fileHelper = new FileHelper();
	PinDmdEditor editor;
	FileChooserUtil fileChooserUtil;
	
	public PaletteHandler(PinDmdEditor editor, Shell shell) {
		super();
		this.editor = editor;
		fileChooserUtil = new FileChooserUtil(shell);
	}
	
	private boolean isNewPaletteName(String text) {
		for (Palette pal : editor.project.palettes) {
			if (pal.name.equals(text))
				return false;
		}
		return true;
	}
	
	public void copyPalettePlaneUpgrade() {
		String name = editor.paletteComboViewer.getCombo().getText();
		if (!isNewPaletteName(name)) {
			name = "new" + UUID.randomUUID().toString().substring(0, 4);
		}
		
		RGB[] actCols = editor.activePalette.colors;
		RGB[] cols = new RGB[actCols.length];
		// copy
		for( int i = 0; i< cols.length; i++) cols[i] = 
				new RGB(actCols[0].red, actCols[0].green, actCols[0].blue );
		cols[1] = new RGB(actCols[1].red, actCols[1].green, actCols[1].blue );
		cols[2] = new RGB(actCols[4].red, actCols[4].green, actCols[4].blue );
		cols[3] = new RGB(actCols[15].red, actCols[15].green, actCols[15].blue );
		
		Palette newPalette = new Palette(cols, editor.project.palettes.size(), name);
		for( Palette pal : editor.project.palettes ) {
			if( pal.sameColors(cols)) {
				editor.activePalette = pal;
				editor.paletteTool.setPalette(editor.activePalette);	
				editor.paletteComboViewer.setSelection(new StructuredSelection(editor.activePalette), true);
				return;
			}
		}
		
		editor.activePalette = newPalette;
		editor.project.palettes.add(editor.activePalette);
		editor.paletteTool.setPalette(editor.activePalette);
		editor.paletteComboViewer.refresh();
		editor.paletteComboViewer.setSelection(new StructuredSelection(editor.activePalette), true);
	}
	
	public void newPalette() {
		String name = editor.paletteComboViewer.getCombo().getText();
		if (!isNewPaletteName(name)) {
			name = "new" + UUID.randomUUID().toString().substring(0, 4);
		}
		editor.activePalette = new Palette(editor.activePalette.colors, editor.project.palettes.size(), name);
		editor.project.palettes.add(editor.activePalette);
		editor.paletteTool.setPalette(editor.activePalette);
		editor.paletteComboViewer.refresh();
		editor.paletteComboViewer.setSelection(new StructuredSelection(editor.activePalette), true);
	}

	public void savePalette() {
		String filename = fileChooserUtil.choose(SWT.SAVE, editor.activePalette.name, new String[] { "*.xml", "*.json" }, new String[] { "Paletten XML", "Paletten JSON" });
		if (filename != null) {
			log.info("store palette to {}", filename);
			fileHelper.storeObject(editor.activePalette, filename);
		}
	}

	public void loadPalette() {
		String filename = fileChooserUtil.choose(SWT.OPEN, null, new String[] { "*.xml", "*.json,", "*.txt", "*.dmc" }, new String[] { "Palette XML",
				"Palette JSON", "smartdmd", "DMC" });
		if (filename != null)
			loadPalette(filename);
	}

	void loadPalette(String filename) {
		if (filename.toLowerCase().endsWith(".txt") || filename.toLowerCase().endsWith(".dmc")) {
			java.util.List<Palette> palettesImported = getImporterByFilename(filename).importFromFile(filename);
			String override = checkOverride(editor.project.palettes, palettesImported);
			if (!override.isEmpty()) {
				MessageBox messageBox = new MessageBox(editor.shell, SWT.ICON_WARNING | SWT.OK | SWT.IGNORE | SWT.ABORT);

				messageBox.setText("Override warning");
				messageBox.setMessage("importing these palettes will override palettes: " + override + "\n");
				int res = messageBox.open();
				if (res != SWT.ABORT) {
					importPalettes(palettesImported, res == SWT.OK);
				}
			} else {
				importPalettes(palettesImported, true);
			}
		} else {
			Palette pal = (Palette) fileHelper.loadObject(filename);
			log.info("load palette from {}", filename);
			editor.project.palettes.add(pal);
			editor.activePalette = pal;
		}
		editor.paletteComboViewer.setSelection(new StructuredSelection(editor.activePalette));
		editor.paletteComboViewer.refresh();
		editor.recentPalettesMenuManager.populateRecent(filename);
	}

	private PaletteImporter getImporterByFilename(String filename) {
		if (filename.toLowerCase().endsWith(".txt")) {
			return new SmartDMDImporter();
		} else if (filename.toLowerCase().endsWith(".dmc")) {
			return new DMCImporter();
		}
		return null;
	}
	
	void importPalettes(java.util.List<Palette> palettesImported, boolean override) {
		Map<Integer, Palette> map = getMap(editor.project.palettes);
		for (Palette p : palettesImported) {
			if (map.containsKey(p.index)) {
				if (override)
					map.put(p.index, p);
			} else {
				map.put(p.index, p);
			}
		}
		editor.project.palettes.clear();
		editor.project.palettes.addAll(map.values());
	}

	String checkOverride(java.util.List<Palette> palettes2, java.util.List<Palette> palettesImported) {
		StringBuilder sb = new StringBuilder();
		Map<Integer, Palette> map = getMap(palettes2);
		for (Palette pi : palettesImported) {
			if (pi.index != 0 && map.containsKey(pi.index)) {
				sb.append(pi.index + ", ");
			}
		}
		return sb.toString();
	}

	private Map<Integer, Palette> getMap(java.util.List<Palette> palettes) {
		Map<Integer, Palette> res = new HashMap<>();
		for (Palette p : palettes) {
			res.put(p.index, p);
		}
		return res;
	}




}