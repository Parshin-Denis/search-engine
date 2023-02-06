package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

public class LemmaFinder {
    private static LuceneMorphology luceneMorph;

    static {
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static HashMap<String, Integer> getLemmaMap(String text){
        HashMap <String, Integer> wordsMap = new HashMap<>();
        String textCleaned = removeHTMLTags(text);
        String[] words = splitToRussianWords(textCleaned);
        for (String word: words) {

            if (!isNormalWord(word)) {
                continue;
            }

            String baseWord = getNormalForm(word);
            if (wordsMap.containsKey(baseWord)) {
                wordsMap.put(baseWord, wordsMap.get(baseWord) + 1);
            }
            else {
                wordsMap.put(baseWord, 1);
            }
        }
        return wordsMap;
    }

    public static TreeMap<Integer, String> getWordIndexes(String text, Set<String> lemmas){
        TreeMap<Integer, String> wordIndexes = new TreeMap<>();
        String textCleaned = removeHTMLTags(text);
        String[] words = splitToRussianWords(textCleaned);
        int wordIndex = 0;
        for (String word: words) {

            if (!isNormalWord(word)) {
                continue;
            }

            String baseWord = getNormalForm(word);
            if (lemmas.contains(baseWord)){
                wordIndex = text.indexOf(word, wordIndex + 1);
                wordIndexes.put(wordIndex, word);
            }
        }
        return wordIndexes;
    }

    public static Set<String> getLemmaSet (String query) {
        Set<String> lemmaSet = new HashSet<>();
        String[] words = splitToRussianWords(query);
        for (String word: words) {

            if (!isNormalWord(word)) {
                continue;
            }

            lemmaSet.add(getNormalForm(word));
        }
        return lemmaSet;
    }
    private static String removeHTMLTags (String text) {
        String regexScriptBlock = "<script.*>[^<>]+</script>";

        String regexAllTags = "<[^<>]+>";

        String regexBeginningLineSpaces = "\\n\\s+";

        return text.replaceAll(regexScriptBlock, "")
                .replaceAll(regexAllTags, "")
                .replaceAll(regexBeginningLineSpaces, "\n");
    }

    public static String[] splitToRussianWords(String text) {
        String regexNotRussianLetters = "[^а-яёА-ЯЁ]+";
        return text.split(regexNotRussianLetters);
    }

    public static boolean isNormalWord(String word){
        if (word.isBlank()) {
            return false;
        }
        String[] particleNames = new String[]{"ПРЕДЛ", "СОЮЗ", "ЧАСТ", "МЕЖД"};
        List<String> wordBaseForms = luceneMorph.getMorphInfo(word.toLowerCase());
        return wordBaseForms.stream().anyMatch(s -> {
            for (String particle: particleNames) {
                if (s.contains(particle)){
                    return false;
                }
            }
            return true;
        });
    }

    public static String getNormalForm(String word){
        List<String> wordBaseForms = luceneMorph.getNormalForms(word.toLowerCase());
        return wordBaseForms.get(0);
    }
}
