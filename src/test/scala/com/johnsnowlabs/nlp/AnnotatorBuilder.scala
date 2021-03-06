package com.johnsnowlabs.nlp

import com.johnsnowlabs.nlp.annotators._
import com.johnsnowlabs.nlp.annotators.ner.crf.{NerCrfApproach, NerCrfModel}
import com.johnsnowlabs.nlp.annotators.ner.regex.NERRegexApproach
import com.johnsnowlabs.nlp.annotators.parser.dep.DependencyParser
import com.johnsnowlabs.nlp.annotators.pos.perceptron.PerceptronApproach
import com.johnsnowlabs.nlp.annotators.sbd.pragmatic.SentenceDetectorModel
import com.johnsnowlabs.nlp.annotators.sda.pragmatic.SentimentDetectorModel
import com.johnsnowlabs.nlp.annotators.sda.vivekn.ViveknSentimentApproach
import com.johnsnowlabs.nlp.annotators.spell.norvig.NorvigSweetingApproach
import org.apache.spark.sql.{Dataset, Row}
import org.scalatest._

/**
  * Created by saif on 02/05/17.
  * Generates different Annotator pipeline paths
  * Place to add different annotator constructions
  */
object AnnotatorBuilder extends FlatSpec { this: Suite =>

  def withDocumentAssembler(dataset: Dataset[Row]): Dataset[Row] = {
    val documentAssembler = new DocumentAssembler()
      .setInputCol("text")
    documentAssembler.transform(dataset)
  }

  def withTokenizer(dataset: Dataset[Row]): Dataset[Row] = {
    val regexTokenizer = new RegexTokenizer()
      .setInputCols(Array("sentence"))
      .setOutputCol("token")
    regexTokenizer.transform(withFullPragmaticSentenceDetector(dataset))
  }

  def withFullStemmer(dataset: Dataset[Row]): Dataset[Row] = {
    val stemmer = new Stemmer()
      .setInputCols(Array("token"))
      .setOutputCol("stem")
    stemmer.transform(withTokenizer(dataset))
  }

  def withFullNormalizer(dataset: Dataset[Row]): Dataset[Row] = {
    val normalizer = new Normalizer()
      .setInputCols(Array("stem"))
      .setOutputCol("normalized")
    normalizer.transform(withFullStemmer(dataset))
  }

  def withFullLemmatizer(dataset: Dataset[Row]): Dataset[Row] = {
    val lemmatizer = new Lemmatizer()
      .setInputCols(Array("token"))
      .setOutputCol("lemma")
      .setLemmaDict("/lemma-corpus/AntBNC_lemmas_ver_001.txt")
    val tokenized = withTokenizer(dataset)
    lemmatizer.transform(tokenized)
  }

  def withFullEntityExtractor(dataset: Dataset[Row]): Dataset[Row] = {
    val entityExtractor = new EntityExtractor()
      .setMaxLen(4)
      .setOutputCol("entity")
    entityExtractor.transform(withFullLemmatizer(dataset))
  }

  def withFullPragmaticSentenceDetector(dataset: Dataset[Row]): Dataset[Row] = {
    val sentenceDetector = new SentenceDetectorModel()
      .setInputCols(Array("document"))
      .setOutputCol("sentence")
    sentenceDetector.transform(dataset)
  }

  def withFullPOSTagger(dataset: Dataset[Row]): Dataset[Row] = {
    val posTagger = new PerceptronApproach()
      .setInputCols(Array("sentence", "token"))
      .setOutputCol("pos")
    posTagger
      .fit(withFullPragmaticSentenceDetector(withTokenizer(dataset)))
      .transform(withFullPragmaticSentenceDetector(withTokenizer(dataset)))
  }

  def withRegexMatcher(dataset: Dataset[Row], rules: Array[(String, String)] = Array.empty[(String, String)], strategy: String): Dataset[Row] = {
    val regexMatcher = new RegexMatcher()
      .setStrategy(strategy)
      .setInputCols(Array("document"))
      .setOutputCol("regex")
    if (rules.nonEmpty) regexMatcher.setRules(rules)
    regexMatcher.transform(dataset)
  }

  def withDateMatcher(dataset: Dataset[Row]): Dataset[Row] = {
    val dateMatcher = new DateMatcher()
      .setOutputCol("date")
    dateMatcher.transform(dataset)
  }

  def withLemmaTaggedSentences(dataset: Dataset[Row]): Dataset[Row] = {
    withFullLemmatizer(withFullPOSTagger(dataset))
  }

  def withPragmaticSentimentDetector(dataset: Dataset[Row]): Dataset[Row] = {
    val sentimentDetector = new SentimentDetectorModel
    sentimentDetector
      .setInputCols(Array("token", "sentence"))
      .setOutputCol("sentiment")
    sentimentDetector.transform(withFullPOSTagger(withFullLemmatizer(dataset)))
  }

  def withViveknSentimentAnalysis(dataset: Dataset[Row]): Dataset[Row] = {
    new ViveknSentimentApproach()
      .setInputCols(Array("token", "sentence"))
      .setOutputCol("vivekn")
      .setPositiveSourcePath("/vivekn/positive/1.txt")
      .setNegativeSourcePath("/vivekn/negative/1.txt")
      .setCorpusPrune(false)
      .fit(dataset)
      .transform(withTokenizer(dataset))
  }

  def withFullSpellChecker(dataset: Dataset[Row]): Dataset[Row] = {
    val spellChecker = new NorvigSweetingApproach()
      .setInputCols(Array("normalized"))
      .setOutputCol("spell")
      .setCorpusPath("/spell/sherlockholmes.txt")
    spellChecker.fit(withFullNormalizer(dataset)).transform(withFullNormalizer(dataset))
  }

  def withNERTagger(dataset: Dataset[Row]): Dataset[Row] = {
    val nerTagger = new NERRegexApproach()
      .setInputCols(Array("sentence"))
      .setOutputCol("ner")
      .setCorpusPath("/ner-corpus/dict.txt")
    nerTagger
      .fit(withFullPragmaticSentenceDetector(dataset))
      .transform(withFullPragmaticSentenceDetector(dataset))
  }

  def withDependencyParser(dataset: Dataset[Row]): Dataset[Row] = {
    val df = withFullPOSTagger(withTokenizer(dataset))
    new DependencyParser()
      .setInputCols(Array("sentence", "pos", "token"))
      .setOutputCol("dependency")
      .fit(df)
      .transform(df)
  }

  def withNerCrfTagger(dataset: Dataset[Row]): Dataset[Row] = {
    val df = withFullPOSTagger(withTokenizer(dataset))

    getNerCrfModel(dataset).transform(df)
  }

  def getNerCrfModel(dataset: Dataset[Row]): NerCrfModel = {
    val df = withFullPOSTagger(withTokenizer(dataset))

    new NerCrfApproach()
      .setInputCols("sentence", "token", "pos")
      .setLabelColumn("label")
      .setMinEpochs(1)
      .setMaxEpochs(3)
      .setDatsetPath("src/test/resources/ner-corpus/test_ner_dataset.txt")
      .setC0(34)
      .setL2(3.0)
      .setOutputCol("ner")
      .fit(df)
  }
}

