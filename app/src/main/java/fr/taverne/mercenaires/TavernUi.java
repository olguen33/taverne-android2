package fr.taverne.mercenaires;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Visual components shared by the original tavern and its connected screens. */
final class TavernUi {
    static final int FOREST=0xff14231f,PAPER=0xffeae2ce,GOLD=0xffd8b76d,INK=0xff25352e;
    static final String[] ARCHETYPE_DESCRIPTIONS={
        "Combattant entraîné qui protège ses alliés et tient la ligne.",
        "Manie les arts magiques pour comprendre et façonner le monde.",
        "Maîtrise les armes à distance et repère ses cibles.",
        "Préfère l’adresse, la discrétion et la ruse à l’affrontement direct.",
        "Se bat avec fougue et puise sa force dans son instinct.",
        "Observe les indices et démêle les secrets.",
        "Parcourt les terres inconnues et trace de nouveaux chemins.",
        "Défend ses convictions et protège les autres.",
        "Agit dans l’ombre, avec précision et patience.",
        "Suit les pistes et connaît les créatures des terres sauvages."
    };
    private TavernUi(){}
    static int dp(Context context,int size){return Math.round(size*context.getResources().getDisplayMetrics().density);}
    static GradientDrawable background(Context context,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(context,radius));return d;}
    static TextView text(Context context,String value,int size,int color,boolean bold){TextView t=new TextView(context);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("serif",Typeface.BOLD));return t;}
    static Button button(Activity activity,String value,Runnable action){Button b=new Button(activity);b.setText(value);b.setTextSize(15);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(background(activity,0xff275340,9));b.setOnClickListener(v->action.run());return b;}
    static LinearLayout panel(Activity activity,LinearLayout body){LinearLayout p=new LinearLayout(activity);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(activity,20),dp(activity,20),dp(activity,20),dp(activity,20));p.setBackground(background(activity,PAPER,14));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(activity,16);body.addView(p,params);return p;}
    static EditText input(Activity activity,LinearLayout parent,String hint,String value,int lines){EditText e=new EditText(activity);e.setSingleLine(lines<=1);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|(lines>1?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));e.setHint(hint);e.setText(value);e.setTextColor(INK);e.setHintTextColor(0xff87918a);e.setTextSize(16);e.setPadding(dp(activity,12),dp(activity,10),dp(activity,12),dp(activity,10));e.setBackground(background(activity,0xfffbfaf4,8));if(lines>1){e.setMinLines(lines);e.setGravity(Gravity.TOP);}parent.addView(e,new LinearLayout.LayoutParams(-1,-2));gap(activity,parent,16);return e;}
    static void gap(Context context,LinearLayout parent,int height){View v=new View(context);parent.addView(v,new LinearLayout.LayoutParams(1,dp(context,height)));}
    static void profileRow(Activity activity,LinearLayout parent,String label,String value){LinearLayout row=new LinearLayout(activity);row.setPadding(dp(activity,9),dp(activity,8),dp(activity,9),dp(activity,8));row.setBackground(background(activity,0xffe2d5b9,4));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(activity,3);parent.addView(row,lp);
        row.addView(text(activity,label,15,INK,true),new LinearLayout.LayoutParams(0,-2,2));TextView content=text(activity,value,16,INK,false);content.setGravity(Gravity.END);row.addView(content,new LinearLayout.LayoutParams(0,-2,1));}
    static void profile(Activity activity,LinearLayout parent,ArchetypeRules.Profile profile,String race,String origin){
        if(profile==null){parent.addView(text(activity,"Aucune fiche d’archétype pour ce personnage.",16,INK,false));return;}
        boolean character=!race.isEmpty()&&!origin.isEmpty();parent.addView(text(activity,character?"Caractéristiques du personnage":"Caractéristiques de base",20,INK,true));gap(activity,parent,9);
        int[] values=character?ArchetypeRules.finalStats(profile,race,origin):profile.stats;
        for(int i=0;i<values.length;i++)profileRow(activity,parent,ArchetypeRules.ABBREVIATIONS[i]+" · "+ArchetypeRules.LABELS[i],Integer.toString(values[i]));
        if(character){gap(activity,parent,8);parent.addView(text(activity,"Race : "+race+" · Origine : "+origin,14,0xff75572f,false));}
        gap(activity,parent,15);parent.addView(text(activity,"Combat",20,INK,true));gap(activity,parent,8);
        profileRow(activity,parent,"Points de vie",Integer.toString(profile.pv));profileRow(activity,parent,"Armure",Integer.toString(profile.armor));
        profileRow(activity,parent,"Dégâts",profile.damage);profileRow(activity,parent,"Pénétration",profile.penetration>0?Integer.toString(profile.penetration):"—");
        gap(activity,parent,15);parent.addView(text(activity,"Compétences et capacités",20,INK,true));gap(activity,parent,8);
        for(String ability:profile.abilities){TextView item=text(activity,"• "+ability,16,INK,false);item.setPadding(dp(activity,5),dp(activity,5),dp(activity,5),dp(activity,5));parent.addView(item);}
    }
}
