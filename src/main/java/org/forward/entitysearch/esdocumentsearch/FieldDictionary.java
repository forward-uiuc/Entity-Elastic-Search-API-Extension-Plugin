package org.forward.entitysearch.esdocumentsearch;

import javafx.util.Pair;

import java.util.Hashtable;

public class FieldDictionary {

    private static FieldDictionary instance = null;
    private Hashtable<String,Pair<String,String>> p = new Hashtable<>();

    private FieldDictionary() {
        p.put("#person", new Pair<>("NamedEntityTag","PERSON"));
        p.put("#location", new Pair<>("NamedEntityTag","LOCATION"));
        p.put("#organization", new Pair<>("NamedEntityTag","ORGANIZATION"));
        p.put("#misc", new Pair<>("NamedEntityTag","MISC"));

        p.put("#date", new Pair<>("NamedEntityTag","DATE"));
        p.put("#time", new Pair<>("NamedEntityTag","TIME"));
        p.put("#duration", new Pair<>("NamedEntityTag","DURATION"));
        p.put("#set", new Pair<>("NamedEntityTag","SET"));

        p.put("#money", new Pair<>("NamedEntityTag","MONEY"));
        p.put("#number", new Pair<>("NamedEntityTag","NUMBER"));
        p.put("#ordinal", new Pair<>("NamedEntityTag","ORDINAL"));
        p.put("#percent", new Pair<>("NamedEntityTag","PERCENT"));

        p.put("#email", new Pair<>("RegexNER","EMAIL"));
        p.put("#url", new Pair<>("RegexNER","URL"));
        p.put("#city", new Pair<>("RegexNER","CITY"));
        p.put("#state_or_province", new Pair<>("RegexNER","STATE_OR_PROVINCE"));
        p.put("#country", new Pair<>("RegexNER","COUNTRY"));
        p.put("#nationality", new Pair<>("RegexNER","NATIONALITY"));
        p.put("#religion", new Pair<>("RegexNER","RELIGION"));
        p.put("#job", new Pair<>("RegexNER","TITLE"));
        p.put("#ideology", new Pair<>("RegexNER","IDEOLOGY"));
        p.put("#criminal_charge", new Pair<>("RegexNER","CRIMINAL_CHARGE"));
        p.put("#cause_of_death", new Pair<>("RegexNER","CAUSE_OF_DEATH"));

        p.put("#anyimg", new Pair<>("Type","img"));
        p.put("#img", new Pair<>("Type","sigimg"));

        p.put("#conference", new Pair<>("ConferenceTag","CONFERENCE"));
        p.put("#conference_acronym", new Pair<>("ConferenceAcronymTag","CONFERENCE_ACRONYM"));
        p.put("#journal", new Pair<>("JournalTag","JOURNAL"));
        p.put("#course", new Pair<>("CourseTag","COURSE"));
        p.put("#topic", new Pair<>("TopicTag","TOPIC"));
        p.put("#professor", new Pair<>("ProfessorTag","PROFESSOR"));
        p.put("#sponsor_agency", new Pair<>("SponsorAgencyTag","SPONSOR_AGENCY"));

        p.put("#phone", new Pair<>("RegexTag","PHONE"));
        p.put("#zip", new Pair<>("RegexTag","ZIPCODE"));
        p.put("#course_number", new Pair<>("RegexTag","COURSE_NO"));
    }

    public static FieldDictionary getInstance() {
        if (instance == null) {
            instance = new FieldDictionary();
        }
        return instance;
    }

    Pair<String, String> getEntity(String entity) {
        return new Pair<>("_entity_" + p.get(entity).getKey(),p.get(entity).getValue());
    }
}
