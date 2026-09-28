package fr.taverne.mercenaires;

import java.util.Arrays;

/** Règles de départ. Les bonus de race et d'origine ne modifient jamais les valeurs de base. */
final class ArchetypeRules {
    static final String[] ABBREVIATIONS={"CT","CC","E","A","F","S","FM","P","I"};
    static final String[] LABELS={"Capacité de tir","Corps à corps","Endurance","Agilité","Force","Sociabilité","Force mentale","Perception","Intelligence"};
    static final class Profile {
        final String name,damage;
        final int[] stats;
        final int pv,armor,penetration;
        final String[] abilities;
        Profile(String name,int[] stats,int pv,int armor,String damage,int penetration,String... abilities){
            this.name=name;this.stats=stats;this.pv=pv;this.armor=armor;this.damage=damage;this.penetration=penetration;this.abilities=abilities;
        }
    }
    static final Profile[] ALL={
        new Profile("Guerrier",new int[]{30,70,50,50,40,40,50,40,40},15,5,"1D10 + F",1,"Parade : +10","Coup précis : CC −10 ; pénétration +2"),
        new Profile("Mage",new int[]{70,10,30,40,20,50,70,50,70},10,1,"—",0,"Soin : rend 1D6 PV","Téléportation : distance en mètres = FM","Boule de feu : 1D10 + I dégâts","Trait du chaos : test FM + test I ; 3D10 + 2 dégâts, pénétration 4"),
        new Profile("Archer",new int[]{70,30,40,70,20,40,30,60,50},12,3,"1D10 + A",2,"Visée : 1 tour = +20 CT"),
        new Profile("Roublard",new int[]{50,50,30,60,30,70,20,60,40},12,2,"2D6 + A",1,"Mensonge : S +10","Vol : A +10","Fuite : A +10","Discrétion : A +10"),
        new Profile("Barbare",new int[]{60,40,70,60,70,20,70,60,10},18,0,"2D10 + F",4,"Pas de parade ; esquive −20","Coup puissant : 2 degrés de réussite nécessaires pour parer"),
        new Profile("Enquêteur",new int[]{30,50,40,50,20,70,60,70,70},12,2,"1D10",6,"Enquête : S / P / I +10"),
        new Profile("Explorateur",new int[]{50,40,60,60,40,30,50,70,50},13,3,"1D10 + 4",3,"Vision de loin : P +10","Fouille : P +10","Orientation : P +10"),
        new Profile("Paladin",new int[]{20,70,60,30,60,20,60,20,50},15,5,"1D10 + 4 + F",0,"Serviable : obligé d'aider ; ne touche que la moitié des primes, donne le reste","Soin : rend 1D6 PV","Lumière de Dieu : aveugle les ennemis ; leur fait perdre 1 tour","Parade : +15","Première ligne : toujours devant le danger"),
        new Profile("Assassin",new int[]{70,50,30,70,30,20,40,50,50},12,2,"2D6 + A",2,"Intrusion : +10","Discrétion : +10","Attaque multiple : attaque 3 fois, doit se reposer au tour d'après"),
        new Profile("Chasseur",new int[]{60,30,40,70,40,30,50,70,40},12,2,"1D10 + A",2,"Pistage : +10","Survie : +10","Peut poser des pièges : 1D10 dégâts, pénétration 5","Compagnon animal : filature +10 ; au combat, 1D4 dégâts, pénétration 1")
    };
    static Profile forName(String name){for(Profile p:ALL)if(p.name.equals(name))return p;return null;}
    static int[] finalStats(Profile profile,String race,String origin){
        int[] result=Arrays.copyOf(profile.stats,profile.stats.length);
        switch(race){case "Humain":result[8]+=10;break;case "Elfe":case "Elf":result[3]+=10;break;case "Nain":result[2]+=10;break;case "Orc":result[4]+=10;break;default:break;}
        switch(origin){case "Citadin":result[5]+=10;break;case "Reclu":case "Reclus":result[6]+=10;break;case "Vie sauvage":result[7]+=10;break;default:break;}
        return result;
    }
    private ArchetypeRules(){}
}
