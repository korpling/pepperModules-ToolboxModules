package de.hu_berlin.german.korpling.saltnpepper.pepperModules.toolboxModules;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

import de.hu_berlin.german.korpling.saltnpepper.pepper.common.DOCUMENT_STATUS;
import de.hu_berlin.german.korpling.saltnpepper.pepper.modules.impl.PepperMapperImpl;
import de.hu_berlin.german.korpling.saltnpepper.salt.SaltFactory;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SAudioDSRelation;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SAudioDataSource;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SSpan;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.STextualDS;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SToken;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.tokenizer.Tokenizer;

public class Toolbox2SaltMapper extends PepperMapperImpl {

	@Override
	public DOCUMENT_STATUS mapSDocument() {
		DocumentStructureReader contentHandler = new DocumentStructureReader();
		this.readXMLResource(contentHandler, getResourceURI());

		return DOCUMENT_STATUS.COMPLETED;
	}

	public class DocumentStructureReader extends DefaultHandler2 {
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
		}

		private StringBuilder currentText = new StringBuilder();

		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			for (int i = start; i < start + length; i++) {
				currentText.append(ch[i]);
			}
		}

		STextualDS primaryText = null;

		EList<SToken> currentTokList = new BasicEList<SToken>();
		SSpan currentTokSpan = SaltFactory.eINSTANCE.createSSpan();

		HashMap<String, String> annoList = new HashMap<String, String>();

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			SAudioDataSource audio = null;

			if (qName != ((ToolboxImporterProperties) getProperties())
					.getRootElement()) {
				// qName is primary text
				if (qName == ((ToolboxImporterProperties) getProperties())
						.getPrimaryTextElement()) {

					// concatenate each primary text in the data to one large
					// STextualDS
					if (((ToolboxImporterProperties) getProperties())
							.concatenateText()) {
						if (primaryText == null) {
							primaryText = SaltFactory.eINSTANCE
									.createSTextualDS();
							primaryText.setSText("");
							getSDocument().getSDocumentGraph().addNode(
									primaryText);
						}
						String text = currentText.toString();

						// concatenated and tokenized
						if (((ToolboxImporterProperties) getProperties())
								.tokenizeText()) {
							Tokenizer tokenizer = new Tokenizer();
							List<String> tokenList = tokenizer
									.tokenizeToString(currentText.toString(),
											null);

							int offset = primaryText.getSText().length();
							primaryText.setSText(primaryText.getSText() + text);
							for (String tok : tokenList) {
								int currentPos = text.indexOf(tok);
								int start = offset + currentPos;
								int end = start + tok.length();
								offset += tok.length() + currentPos;
								text = text
										.substring(currentPos + tok.length());

								SToken currTok = getSDocument()
										.getSDocumentGraph().createSToken(
												primaryText, start, end);

								// remember all SToken
								currentTokList.add(currTok);
							}

						} else {
							// concatenated and not tokenized
							primaryText.setSText(primaryText.getSText() + text);
							SToken currTok = getSDocument().getSDocumentGraph()
									.createSToken(
											primaryText,
											primaryText.getSText().length()
													- text.length(),
											primaryText.getSText().length());
							currentTokList.add(currTok);
						}

					} else {
						// not concatenated
						primaryText = getSDocument().getSDocumentGraph()
								.createSTextualDS(currentText.toString());

						if (((ToolboxImporterProperties) getProperties())
								.tokenizeText()) {
							// not concatenated but tokenized
							currentTokList = getSDocument().getSDocumentGraph()
									.tokenize();

						} else {
							// not concatenated and not tokenized
							SToken currentTok = getSDocument()
									.getSDocumentGraph().createSToken(
											primaryText, 0,
											primaryText.getSText().length());
							currentTokList.add(currentTok);
						}

					}

					currentTokSpan = getSDocument().getSDocumentGraph()
							.createSSpan(currentTokList);
					// create annotation of tags that were loaded before the
					// actual primary text
					if (!annoList.isEmpty()) {

						for (Entry<String, String> anno : annoList.entrySet()) {
							currentTokSpan.createSAnnotation(null, anno
									.getKey().toString(), anno.getValue()
									.toString());
						}
					}

				}

				// annotations are only associated with the current primary text
				if (((ToolboxImporterProperties) getProperties())
						.associateWithAllToken() == null
						|| ((ToolboxImporterProperties) getProperties())
								.associateWithAllToken().isEmpty()) {

					// qName is not an audio tag, a segemnting tag or the
					// tag
					// that holds the primary text
					if (qName != ((ToolboxImporterProperties) getProperties())
							.getPrimaryTextElement()
							&& qName != ((ToolboxImporterProperties) getProperties())
									.getAudioRecordElement()
							&& qName != ((ToolboxImporterProperties) getProperties())
									.getSegmentingElement()) {

						// save all annotations except audio and primary
						// text as span
						if (!currentTokList.isEmpty()) {
							// create a new span for each annotation
							if (((ToolboxImporterProperties) getProperties())
									.createNewSpan()) {
								currentTokSpan = getSDocument()
										.getSDocumentGraph().createSSpan(
												currentTokList);
							}
							currentTokSpan.createSAnnotation(null, qName,
									currentText.toString());

						} else {
							annoList.put(qName, currentText.toString());
						}

						if (qName == ((ToolboxImporterProperties) getProperties())
								.getAudioRecordElement()) {
							audio = SaltFactory.eINSTANCE
									.createSAudioDataSource();

							File audioFile = new File(currentText.toString());

							if (audioFile.exists()) {
								audio.setSAudioReference(URI
										.createFileURI(currentText.toString()));
							} else {
								URI absPath = getResourceURI()
										.appendFileExtension(
												currentText.toString());
								File audioFileAbs = new File(absPath.path());
								if (audioFileAbs.exists()) {
									audio.setSAudioReference(URI
											.createFileURI(absPath.path()));
								} else {
									ToolboxImporter.logger
											.debug("No audio file found.");
								}
							}

							for (SToken tok : currentTokList) {
								SAudioDSRelation audioRel = SaltFactory.eINSTANCE
										.createSAudioDSRelation();
								audioRel.setSToken(tok);
								audioRel.setSAudioDS(audio);
								getSDocument().getSDocumentGraph()
										.addSRelation(audioRel);
							}
						}
					} else {
						// use only one span for all annotations
						// qName is not an audio tag, a segemnting tag or the
						// tag
						// that holds the primary text
						if (qName != ((ToolboxImporterProperties) getProperties())
								.getPrimaryTextElement()
								&& qName != ((ToolboxImporterProperties) getProperties())
										.getAudioRecordElement()
								&& qName != ((ToolboxImporterProperties) getProperties())
										.getSegmentingElement()) {

							// save all annotations except audio and primary
							// text as annotation in one
							// span
							if (!currentTokList.isEmpty()) {
								currentTokSpan.createSAnnotation(null, qName,
										currentText.toString());

							} else {
								annoList.put(qName, currentText.toString());
							}
						}
					}
				} else {
					// annotations are associated with all primary text tags of
					// the current segment
				}

				// reset the currentTokList for each new segment
				if (qName == ((ToolboxImporterProperties) getProperties())
						.getSegmentingElement()) {
					currentTokList = new BasicEList<SToken>();
					annoList = new HashMap<String, String>();
				}

				currentText = new StringBuilder();
			}
		}
	}

}
