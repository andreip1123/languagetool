/* LanguageTool, a natural language style checker
 * Copyright (C) 2019 Daniel Naber (http://www.danielnaber.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool;

import org.languagetool.rules.*;
import org.languagetool.rules.patterns.AbstractPatternRule;
import org.languagetool.rules.patterns.PatternRuleLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class LanguageSpecificTest {

  protected void runTests(Language lang) throws IOException {
    new WordListValidatorTest().testWordListValidity(lang);
    testNoQuotesAroundSuggestion(lang);
    testJavaRules();
  }

  private final static Map<String, Integer> idToExpectedMatches = new HashMap<>();
  static {
    idToExpectedMatches.put("STYLE_REPEATED_WORD_RULE_DE", 2);
  }

  private void testJavaRules() throws IOException {
    Map<String,String> idsToClassName = new HashMap<>();
    Set<Class> ruleClasses = new HashSet<>();
    for (Language language : Languages.getWithDemoLanguage()) {
      JLanguageTool lt = new JLanguageTool(language);
      List<Rule> allRules = lt.getAllRules();
      for (Rule rule : allRules) {
        if (!(rule instanceof AbstractPatternRule)) {
          assertIdUniqueness(idsToClassName, ruleClasses, language, rule);
          assertIdValidity(language, rule);
          assertTrue(rule.supportsLanguage(language));
          testExamples(rule, lt);
        }
      }
    }
  }

  // no quotes needed around <suggestion>...</suggestion> in XML:
  private void testNoQuotesAroundSuggestion(Language lang) throws IOException {
    if (lang.getShortCode().equals("fa") || lang.getShortCode().equals("zh")) {
      // ignore languages not maintained anyway
      System.out.println("Skipping testNoQuotesAroundSuggestion for " + lang.getName());
      return;
    }
    String dirBase = JLanguageTool.getDataBroker().getRulesDir() + "/" + lang.getShortCode() + "/";
    for (String ruleFileName : lang.getRuleFileNames()) {
      if (ruleFileName.contains("-test-")) {
        continue;
      }
      InputStream is = this.getClass().getResourceAsStream(ruleFileName);
      List<AbstractPatternRule> rules = new PatternRuleLoader().getRules(is, dirBase + "/" + ruleFileName);
      for (AbstractPatternRule rule : rules) {
        String message = rule.getMessage();
        if (message.matches(".*['\"«»“”’]<suggestion.*") && message.matches(".*</suggestion>['\"«»“”’].*")) {
          fail(lang.getName() + " rule " + rule.getFullId() + " uses quotes around <suggestion>...<suggestion> in its <message>, this should be avoided: '" + message + "'");
        }
      }
    }
  }

  protected void testDemoText(Language lang, String text, List<String> expectedMatchIds) throws IOException {
    JLanguageTool lt = new JLanguageTool(lang);
    List<RuleMatch> matches = lt.check(text);
    int i = 0;
    List<String> actualRuleIds = new ArrayList<>();
    for (RuleMatch match : matches) {
      actualRuleIds.add(match.getRule().getId());
    }
    if (expectedMatchIds.size() != actualRuleIds.size()) {
      failTest(lang, text, expectedMatchIds, actualRuleIds);
    }
    for (String actualRuleId : actualRuleIds) {
      if (!expectedMatchIds.get(i).equals(actualRuleId)) {
        failTest(lang, text, expectedMatchIds, actualRuleIds);
      }
      i++;
    }
  }

  private void failTest(Language lang, String text, List<String> expectedMatchIds, List<String> actualRuleIds) {
    fail("The website demo text matches for " + lang + " have changed. Demo text:\n" + text +
            "\nExpected rule matches:\n" + expectedMatchIds + "\nActual rule matches:\n" + actualRuleIds);
  }

  private void assertIdUniqueness(Map<String,String> ids, Set<Class> ruleClasses, Language language, Rule rule) {
    String ruleId = rule.getId();
    if (ids.containsKey(ruleId) && !ruleClasses.contains(rule.getClass())) {
      throw new RuntimeException("Rule id occurs more than once: '" + ruleId + "', one of them " +
              rule.getClass() + ", the other one " + ids.get(ruleId) + ", language: " + language);
    }
    ids.put(ruleId, rule.getClass().getName());
    ruleClasses.add(rule.getClass());
  }

  private void assertIdValidity(Language language, Rule rule) {
    String ruleId = rule.getId();
    if (!ruleId.matches("^[A-Z_][A-Z0-9_]+$")) {
      throw new RuntimeException("Invalid character in rule id: '" + ruleId + "', language: "
              + language + ", only [A-Z0-9_] are allowed and the first character must be in [A-Z_]");
    }
  }

  private void testExamples(Rule rule, JLanguageTool lt) throws IOException {
    testCorrectExamples(rule, lt);
    testIncorrectExamples(rule, lt);
  }

  private void testCorrectExamples(Rule rule, JLanguageTool lt) throws IOException {
    List<CorrectExample> correctExamples = rule.getCorrectExamples();
    for (CorrectExample correctExample : correctExamples) {
      String input = cleanMarkers(correctExample.getExample());
      enableOnlyOneRule(lt, rule);
      List<RuleMatch> ruleMatches = lt.check(input);
      assertEquals("Got unexpected rule match for correct example sentence:\n"
              + "Text: " + input + "\n"
              + "Rule: " + rule.getId() + "\n"
              + "Matches: " + ruleMatches, 0, ruleMatches.size());
    }
  }

  private void testIncorrectExamples(Rule rule, JLanguageTool lt) throws IOException {
    List<IncorrectExample> incorrectExamples = rule.getIncorrectExamples();
    for (IncorrectExample incorrectExample : incorrectExamples) {
      String input = cleanMarkers(incorrectExample.getExample());
      enableOnlyOneRule(lt, rule);
      List<RuleMatch> ruleMatches = lt.check(input);
      assertEquals("Did not get the expected rule match for the incorrect example sentence:\n"
              + "Text: " + input + "\n"
              + "Rule: " + rule.getId() + "\n"
              + "Matches: " + ruleMatches, (int)idToExpectedMatches.getOrDefault(rule.getId(), 1), ruleMatches.size());
    }
  }

  private void enableOnlyOneRule(JLanguageTool lt, Rule ruleToActivate) {
    for (Rule rule : lt.getAllRules()) {
      lt.disableRule(rule.getId());
    }
    lt.enableRule(ruleToActivate.getId());
  }

  private String cleanMarkers(String example) {
    return example.replace("<marker>", "").replace("</marker>", "");
  }

}
