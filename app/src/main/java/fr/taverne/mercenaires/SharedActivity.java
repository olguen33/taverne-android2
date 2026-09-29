package fr.taverne.mercenaires;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Online game. Every mutation waits for server confirmation before refreshing the screen. */
public final class SharedActivity extends Activity {
    private static final String URL="https://dgvvocfpxflmfqaaakoe.supabase.co";
    private static final String KEY="sb_publishable_BW_VQsD-ymcyjgUblfUkDw_bkadPgIu";
    private static final String PREF="online_session";
    private static final String[] TABS={"Accueil","Compte","Personnages","Contrats","Campagne","Boutique","Rumeurs","Carte","Archétypes","Dés","Espace MJ"};
    private static final String[] RACES={"Humain","Orc","Nain","Elfe"};
    private static final String[] ORIGINS={"Citadin","Reclu","Vie sauvage"};
    private static final String[] DAYS={"Lundi","Mardi","Mercredi","Jeudi","Vendredi","Samedi","Dimanche"};
    private final SupabaseGateway api=new SupabaseGateway(URL,KEY);
    private SharedPreferences prefs;
    private UpdateManager updates;
    private LinearLayout root,body;
    private ScrollView scroll;
    private String page="Accueil",selectedCharacter,selectedContract,selectedCampaignCharacter,userId="",pseudo="",renderedKey;
    private int expandedCampaignPanel=-1;
    private int selectedArchetype=-1;
    private boolean contractEditor=false;
    private String editingContractId;
    private String pendingContractId;
    private String pendingRumourId;
    private boolean registering=false,busy=false,draftDirty=false,restoringSession=false;
    private String loadingError="";
    private boolean completedContracts=false;
    private final Handler refreshHandler=new Handler(Looper.getMainLooper());
    private final Runnable periodicRefresh=new Runnable(){@Override public void run(){
        if(!userId.isEmpty()&&!busy)refresh();
        refreshHandler.postDelayed(this,15000);
    }};
    private JSONArray characters=new JSONArray(),contracts=new JSONArray(),slots=new JSONArray(),participants=new JSONArray(),votes=new JSONArray(),messages=new JSONArray(),catalog=new JSONArray(),profiles=new JSONArray(),names=new JSONArray(),rumours=new JSONArray(),campaignEntries=new JSONArray();
    private final Map<String,Integer> positions=new HashMap<>();
    private int paper=0xffeae2ce,forest=0xff14231f,ink=0xff25352e,gold=0xffd8b76d;
    interface Work { void run() throws Exception; }
    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences(PREF,MODE_PRIVATE);updates=new UpdateManager(this);String requested=getIntent().getStringExtra("page");if(requested!=null&&java.util.Arrays.asList(TABS).contains(requested))page=requested;getWindow().setStatusBarColor(forest);getWindow().setNavigationBarColor(forest);if(!prefs.getString("refresh","").isEmpty()){restoreSession();}else render();}
    private void restoreSession(){if(busy)return;restoringSession=true;loadingError="";render();task(()->{String refresh=prefs.getString("refresh","");if(refresh.isEmpty())throw new IllegalStateException("Session expirée. Connecte-toi à nouveau.");saveSession(api.refresh(refresh));load();},()->{restoringSession=false;loadingError="";render();});}
    @Override protected void onResume(){super.onResume();updates.resume();if(!userId.isEmpty()&&!busy)refresh();refreshHandler.removeCallbacks(periodicRefresh);refreshHandler.postDelayed(periodicRefresh,15000);}
    @Override protected void onPause(){refreshHandler.removeCallbacks(periodicRefresh);super.onPause();}
    private int dp(int n){return TavernUi.dp(this,n);}
    private GradientDrawable bg(int color){return TavernUi.background(this,color,14);}
    private TextView txt(String s,int size,int color){return TavernUi.text(this,s,size,color,size>=20);}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private void gap(LinearLayout l,int h){TavernUi.gap(this,l,h);}
    private LinearLayout card(){return TavernUi.panel(this,body);}
    private Button button(LinearLayout p,String s,Runnable run){Button b=TavernUi.button(this,s,run);p.addView(b);return b;}
    private EditText field(LinearLayout p,String label,String value,int lines){TextView t=txt(label,16,ink);t.setTypeface(Typeface.create("serif",Typeface.BOLD));p.addView(t);gap(p,6);EditText e=TavernUi.input(this,p,label,value,lines);
        e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){draftDirty=true;}public void afterTextChanged(Editable s){}});return e;}
    private Spinner spinner(LinearLayout p,String label,String[] choices,String value){p.addView(txt(label,15,ink));Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,choices);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);p.addView(s);for(int i=0;i<choices.length;i++)if(choices[i].equals(value))s.setSelection(i);gap(p,8);return s;}
    private Spinner newCharacterChoice(LinearLayout p,String label,String[] choices,String[] displayed){p.addView(txt(label,16,ink));String[] items=new String[displayed.length+1];items[0]="Choisir…";System.arraycopy(displayed,0,items,1,displayed.length);
        Spinner s=spinner(p,"",items,"");s.setTag(choices);return s;}
    private String chosenCharacterValue(Spinner spinner){int index=spinner.getSelectedItemPosition();String[] choices=(String[])spinner.getTag();return index<=0?"":choices[index-1];}
    private void notice(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void task(Work work,Runnable success){if(busy)return;busy=true;new Thread(()->{String error=null;try{work.run();}catch(Exception e){
            try{if(e.getMessage()!=null&&e.getMessage().contains("(401)")&&!prefs.getString("refresh","").isEmpty()){
                    saveSession(api.refresh(prefs.getString("refresh","")));work.run();
                }else throw e;
            }catch(Exception retry){String detail=retry.getMessage();error=retry instanceof java.net.UnknownHostException||retry instanceof java.net.SocketTimeoutException?"Connexion indisponible. Réessaie quand le réseau revient.":detail!=null&&(detail.contains("over_email_send_rate_limit")||detail.contains("email rate limit"))?"Envoi d’e-mails temporairement limité. Réessaie plus tard.":detail==null?"Erreur réseau":detail;}
        }String failure=error;runOnUiThread(()->{busy=false;if(failure!=null){if(restoringSession){restoringSession=false;loadingError=failure.length()>180?failure.substring(0,180):failure;if(failure.contains("invalid_grant")||failure.contains("refresh_token_not_found")){prefs.edit().remove("refresh").apply();userId="";api.signOut();}render();}else notice(failure.length()>180?failure.substring(0,180):failure);}else success.run();});}).start();}
    private void saveSession(JSONObject result)throws Exception{
        String token=result.getString("access_token"),refresh=result.getString("refresh_token");api.useToken(token);
        JSONObject user=result.getJSONObject("user");userId=user.getString("id");prefs.edit().putString("refresh",refresh).apply();
    }
    private void load()throws Exception{
        profiles=api.list("profiles","select=id,pseudo");
        characters=api.list("characters","select=*");contracts=api.list("contracts","select=*&order=id.desc");
        slots=api.list("slots","select=*");participants=api.list("participants","select=*");votes=api.list("votes","select=*");
        messages=api.list("messages","select=*&order=id.asc");catalog=api.list("catalog","select=*&active=eq.true");
        rumours=api.list("rumours","select=*");campaignEntries=api.list("campaign_entries","select=*&order=created_at.desc");names=api.names();
        pseudo=userId;for(int i=0;i<profiles.length();i++){JSONObject p=profiles.getJSONObject(i);if(userId.equals(p.optString("id")))pseudo=p.optString("pseudo",userId);}
    }
    private void refresh(){task(()->load(),()->{if(!draftDirty)render();});}
    private void submit(Work work){task(()->{work.run();load();},this::render);}
    private JSONObject find(JSONArray a,String id){for(int i=0;i<a.length();i++){JSONObject v=a.optJSONObject(i);if(v!=null&&id.equals(v.optString("id")))return v;}return null;}
    private String name(String id){JSONObject n=find(names,id);return n==null?"Personnage":n.optString("name","Personnage");}
    private String owner(String id){JSONObject n=find(names,id);return n==null?"":n.optString("owner_id");}
    private String player(String id){JSONObject p=find(profiles,id);return p==null?"MJ":p.optString("pseudo","MJ");}
    private void render(){
        if(scroll!=null&&renderedKey!=null)positions.put(renderedKey,scroll.getScrollY());scroll=null;
        if(!restoringSession&&loadingError.isEmpty()&&!userId.isEmpty()&&"Accueil".equals(page)){finish();return;}
        renderedKey=page+":"+selectedCharacter+":"+selectedContract+":"+selectedCampaignCharacter+":"+expandedCampaignPanel+":"+selectedArchetype+":"+contractEditor;
        root=col();root.setBackgroundColor(forest);setContentView(root);
        draftDirty=false;if(userId.isEmpty()||restoringSession||!loadingError.isEmpty()){authScreen();return;}
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(14),dp(18),dp(12));root.addView(top);
        TextView back=txt("‹",32,gold);back.setGravity(Gravity.CENTER);back.setContentDescription("Retour à la Taverne");top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->finish());
        TextView title=txt("La Taverne",24,0xffe8e1d0);title.setTypeface(Typeface.SERIF,Typeface.BOLD);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView options=txt("⚙",27,0xffe8e1d0);options.setGravity(Gravity.CENTER);options.setContentDescription("Compte et actualisation");top.addView(options,new LinearLayout.LayoutParams(dp(52),dp(52)));
        options.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(pseudo)
            .setItems(new String[]{"Actualiser","Vérifier les mises à jour","Ambiance de la Taverne : "+(getSharedPreferences("audio_settings",MODE_PRIVATE).getBoolean("music_enabled",true)?"activée":"désactivée"),"Déconnexion"},(dialog,which)->{
                if(which==0)refresh();else if(which==1)updates.check(true);
                else if(which==2){SharedPreferences audio=getSharedPreferences("audio_settings",MODE_PRIVATE);audio.edit().putBoolean("music_enabled",!audio.getBoolean("music_enabled",true)).apply();}
                else{prefs.edit().remove("refresh").apply();api.signOut();userId="";render();}}).show());
        if("Carte".equals(page)&&selectedCharacter==null&&selectedContract==null&&!contractEditor)mapScreen();else{
        scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=col();body.setPadding(dp(16),dp(16),dp(16),dp(30));scroll.addView(body);
        if(contractEditor)contractFormScreen();else if(selectedCharacter!=null)characterDetail();else if(selectedContract!=null)contractDetail();else switch(page){
            case "Personnages":characterList();break;case "Contrats":contractList();break;case "Boutique":shop();break;
            case "Rumeurs":rumourList();break;case "Placer une rumeur":rumourEditor();break;case "Mes rumeurs":myRumours();break;
            case "Campagne":campaignScreen();break;case "Archétypes":archetypes();break;case "Dés":dice();break;case "Espace MJ":masterScreen();break;case "Compte":accountScreen();break;default:home();}
        Integer y=positions.get(renderedKey);if(y!=null)scroll.post(()->scroll.scrollTo(0,y));
        }
        if("Campagne".equals(page)&&expandedCampaignPanel>=0)return;
        HorizontalScrollView navScroll=new HorizontalScrollView(this);navScroll.setHorizontalScrollBarEnabled(false);navScroll.setFillViewport(true);navScroll.setBackgroundColor(0xff192a25);root.addView(navScroll,new LinearLayout.LayoutParams(-1,dp(68)));
        LinearLayout nav=new LinearLayout(this);navScroll.addView(nav);for(String tab:new String[]{"Contrats","Carte","Rumeurs","Personnages","Boutique","Espace MJ"}){
            TextView item=txt(tab,14,tab.equals(page)?gold:0xffb1bfb4);item.setGravity(Gravity.CENTER);item.setPadding(dp(12),0,dp(12),0);item.setMinWidth(dp(94));nav.addView(item,new LinearLayout.LayoutParams(-2,-1));item.setOnClickListener(v->{page=tab;selectedCharacter=null;selectedContract=null;selectedCampaignCharacter=null;expandedCampaignPanel=-1;selectedArchetype=-1;render();});}
    }
    private void authScreen(){ScrollView s=new ScrollView(this);root.addView(s);body=col();body.setPadding(dp(22),dp(55),dp(22),dp(22));s.addView(body);body.addView(txt("La Taverne · Compagnie en ligne",30,gold));gap(body,16);
        if(restoringSession){LinearLayout loading=card();loading.addView(txt("Chargement de la compagnie…",20,ink));button(loading,"Retour à la Taverne",this::finish);return;}
        if(!loadingError.isEmpty()){LinearLayout error=card();error.addView(txt("Chargement interrompu",21,ink));error.addView(txt(loadingError,15,ink));if(!prefs.getString("refresh","").isEmpty())button(error,"Réessayer",this::restoreSession);button(error,"Retour à la Taverne",this::finish);}
        LinearLayout p=card();p.addView(txt(registering?"Créer un compte en ligne":"Connexion en ligne",23,ink));gap(p,12);
        EditText email=field(p,"Adresse e-mail","",1);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password=field(p,"Mot de passe","",1);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText nickname=registering?field(p,"Pseudo visible","",1):null;
        button(p,registering?"Créer mon compte":"Se connecter",()->{
            String mail=email.getText().toString().trim(),secret=password.getText().toString();if(!mail.contains("@")||secret.length()<8){notice("Adresse e-mail ou mot de passe invalide (8 caractères minimum).");return;}
            if(registering){String n=nickname.getText().toString().trim();if(n.length()<3||n.length()>32){notice("Pseudo de 3 à 32 caractères requis.");return;}prefs.edit().putString("pending_pseudo",n).apply();task(()->{JSONObject r=api.signUp(mail,secret);if(r.has("access_token")&&!r.isNull("access_token")){saveSession(r);try{api.rpc("set_pseudo",new JSONObject().put("p_pseudo",n));}catch(Exception ignored){}load();}},()->{notice(userId.isEmpty()?"Compte créé. Confirme l’e-mail reçu, puis connecte-toi.":"Compte créé.");registering=false;render();});}
            else task(()->{saveSession(api.signIn(mail,secret));prefs.edit().putBoolean("legacy_mode",false).apply();String pending=prefs.getString("pending_pseudo","");if(!pending.isEmpty()){try{api.rpc("set_pseudo",new JSONObject().put("p_pseudo",pending));}catch(Exception ignored){}prefs.edit().remove("pending_pseudo").apply();}load();},this::finish);
        });button(p,registering?"J’ai déjà un compte":"Créer un compte",()->{registering=!registering;render();});
        LinearLayout legacy=card();legacy.addView(txt("Ancien compte sur ce téléphone",19,ink));legacy.addView(txt("Tes données locales restent accessibles dans l’ancienne interface. Après connexion en ligne, tu pourras importer tes personnages.",15,ink));button(legacy,"Retour à l’application locale",()->{prefs.edit().putBoolean("legacy_mode",true).apply();finish();});
    }
    private void home(){body.addView(txt("Compte de "+pseudo,24,ink));}
    private void accountScreen(){LinearLayout p=card();p.addView(txt("Compte de "+pseudo,24,ink));gap(p,8);
        button(p,"Changer mon pseudo",()->{EditText newName=new EditText(this);newName.setText(pseudo);new AlertDialog.Builder(this).setTitle("Pseudo visible").setView(newName).setNegativeButton("Annuler",null)
            .setPositiveButton("Enregistrer",(d,w)->submit(()->api.rpc("set_pseudo",new JSONObject().put("p_pseudo",newName.getText().toString().trim())))).show();});
        button(p,"Importer mes personnages locaux",this::importDialog);button(p,"Ouvrir l’ancienne interface locale",()->{prefs.edit().putBoolean("legacy_mode",true).apply();finish();});}
    private void masterScreen(){body.addView(txt("Espace MJ",38,0xffe8e1d0));gap(body,24);
        for(String entry:new String[]{"Publier contrat","Mes contrats","Placer une rumeur","Mes rumeurs","Cartes","Mes cartes","Bestiaire"}){
            LinearLayout p=card();button(p,entry+"  ›",()->{
                switch(entry){case "Publier contrat":contractForm(null);break;
                    case "Mes contrats":page="Contrats";completedContracts=false;render();break;
                    case "Placer une rumeur":pendingRumourId=UUID.randomUUID().toString();page=entry;render();break;
                    case "Mes rumeurs":page=entry;render();break;
                    case "Cartes":page="Carte";render();break;
                    case "Mes cartes":notice("Les cartes de donjon locales restent accessibles dans l’ancienne interface.");break;
                    case "Bestiaire":notice("Cet espace sera développé plus tard.");break;}
            });}
    }
    private void campaignScreen(){
        if(selectedCampaignCharacter==null){body.addView(txt("Campagne",38,0xffe8e1d0));gap(body,24);
            LinearLayout intro=card();intro.addView(txt("Choisis ton personnage",24,ink));gap(intro,10);
            intro.addView(txt("Ouvre son espace de campagne pour consulter sa fiche, son inventaire, ses dés et ses notes.",16,ink));
            if(characters.length()==0)card().addView(txt("Aucun personnage pour ce compte. Crée un personnage dans l’onglet Personnages.",17,ink));
            for(int i=0;i<characters.length();i++){JSONObject c=characters.optJSONObject(i);LinearLayout entry=card();entry.addView(txt(c.optString("name"),25,ink));
                gap(entry,6);entry.addView(txt(c.optString("race")+" · "+c.optString("archetype")+" · "+c.optString("origin"),15,ink));
                button(entry,"Choisir ce personnage",()->{selectedCampaignCharacter=c.optString("id");render();});}
            sharedCampaignEntries();return;
        }
        JSONObject c=find(characters,selectedCampaignCharacter);if(c==null){selectedCampaignCharacter=null;campaignScreen();return;}
        button(body,"‹  Changer de personnage",()->{selectedCampaignCharacter=null;expandedCampaignPanel=-1;render();});gap(body,16);
        body.addView(txt(c.optString("name"),38,0xffe8e1d0));gap(body,24);
        String[] sections={"Fiche du personnage","Inventaire","Dés","Note"};
        for(int i=0;i<sections.length;i++){if(expandedCampaignPanel>=0&&expandedCampaignPanel!=i)continue;
            final int index=i;LinearLayout panel=card();LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);panel.addView(heading);
            heading.addView(txt(sections[i],22,ink),new LinearLayout.LayoutParams(0,-2,1));TextView expand=txt(expandedCampaignPanel==i?"↙":"⤢",26,ink);
            expand.setGravity(Gravity.CENTER);expand.setContentDescription(expandedCampaignPanel==i?"Réduire "+sections[i]:"Agrandir "+sections[i]);
            heading.addView(expand,new LinearLayout.LayoutParams(dp(44),dp(44)));expand.setOnClickListener(v->{expandedCampaignPanel=expandedCampaignPanel==index?-1:index;render();});gap(panel,12);
            if(i==0){panel.addView(txt(c.optString("race")+" · "+c.optString("archetype")+" · "+c.optString("origin"),17,ink));gap(panel,12);
                TavernUi.profile(this,panel,ArchetypeRules.forName(c.optString("archetype")),c.optString("race"),c.optString("origin"));
                if(!c.optString("sheet").isEmpty())panel.addView(txt(c.optString("sheet"),16,ink));
                button(panel,"Ouvrir et modifier la fiche",()->{page="Personnages";selectedCharacter=c.optString("id");render();});}
            else if(i==1){panel.addView(txt("Bourse : "+c.optLong("gold")+" pièces d’or",17,ink));EditText inventory=field(panel,"Un objet et sa quantité par ligne",c.optString("inventory"),5);
                button(panel,"Enregistrer l’inventaire",()->submit(()->api.updateCharacterField(c.optString("id"),"inventory",inventory.getText().toString())));}
            else if(i==2)campaignDice(panel);
            else{EditText notes=field(panel,"Notes de campagne…",c.optString("campaign_notes"),6);
                button(panel,"Enregistrer la note",()->submit(()->api.updateCharacterField(c.optString("id"),"campaign_notes",notes.getText().toString())));}
        }
        if(expandedCampaignPanel<0)sharedCampaignEntries();
    }
    private void campaignDice(LinearLayout panel){TextView result=txt("Choisis un dé et sa quantité, puis touche D pour lancer.",18,ink);
        java.security.SecureRandom random=new java.security.SecureRandom();for(int sides:new int[]{4,6,8,10,12,20,100}){
            int[] count={1};LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);panel.addView(row);
            button(row,"D"+sides,()->{int total=0;StringBuilder rolls=new StringBuilder();for(int k=0;k<count[0];k++){int value=1+random.nextInt(sides);total+=value;if(k>0)rolls.append(" + ");rolls.append(value);}result.setText(count[0]+"D"+sides+" : "+rolls+" = "+total);});
            TextView number=txt("1",19,ink);button(row,"‹",()->{if(count[0]>1)count[0]--;number.setText(Integer.toString(count[0]));});row.addView(number);
            button(row,"›",()->{if(count[0]<100)count[0]++;number.setText(Integer.toString(count[0]));});}
        gap(panel,8);panel.addView(result);
    }
    private void sharedCampaignEntries(){LinearLayout compose=card();compose.addView(txt("Parchemin de la compagnie",23,ink));gap(compose,12);
        EditText title=field(compose,"Titre","",1),content=field(compose,"Informations à partager avec tous les joueurs","",5);
        button(compose,"Publier dans la campagne",()->{String t=title.getText().toString().trim(),b=content.getText().toString().trim();
            if(t.isEmpty()||t.length()>120||b.isEmpty()||b.length()>8000){notice("Titre ou contenu invalide.");return;}
            submit(()->api.insertCampaignEntry(new JSONObject().put("author_id",userId).put("title",t).put("body",b)));
        });
        if(campaignEntries.length()==0)card().addView(txt("Aucune information partagée pour le moment.",16,ink));
        for(int i=0;i<campaignEntries.length();i++){JSONObject entry=campaignEntries.optJSONObject(i);if(entry==null)continue;
            LinearLayout parchment=card();parchment.addView(txt(entry.optString("title"),24,ink));gap(parchment,10);
            parchment.addView(txt(entry.optString("body"),17,ink));gap(parchment,12);
            parchment.addView(txt("Publié par "+player(entry.optString("author_id")),14,0xff75572f));
            if(userId.equals(entry.optString("author_id")))button(parchment,"Supprimer",()->new AlertDialog.Builder(this)
                .setTitle("Supprimer cette information ?").setNegativeButton("Annuler",null)
                .setPositiveButton("Supprimer",(d,w)->submit(()->api.remove("campaign_entries",entry.optString("id")))).show());
        }
    }
    private void importDialog(){LinearLayout p=col();p.setPadding(dp(16),dp(12),dp(16),0);EditText old=field(p,"Ancien pseudo local","",1),password=field(p,"Ancien mot de passe local","",1);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Vérifier l’ancien compte").setView(p).setNegativeButton("Annuler",null).setPositiveButton("Continuer",(d,w)->{
            String n=old.getText().toString().trim();char[] secret=password.getText().toString().toCharArray();task(()->{
                TavernDb db=new TavernDb(this);long id;try{id=db.authenticate(n,secret);}finally{java.util.Arrays.fill(secret,'\0');}
                if(id<0)throw new IllegalArgumentException("Ancien pseudo ou mot de passe incorrect.");
                JSONArray data=db.exportLocalCharacters(id);runOnUiThread(()->previewImport(n,data));
            },()->{});
        }).show();
    }
    private void previewImport(String old,JSONArray data){if(data.length()==0){notice("Aucun personnage à transférer.");return;}StringBuilder list=new StringBuilder();for(int i=0;i<data.length();i++)list.append("• ").append(data.optJSONObject(i).optString("name")).append("\n");
        new AlertDialog.Builder(this).setTitle("Importer "+data.length()+" personnage(s) ?")
            .setMessage(list+"\nLes fichiers joints, contrats, votes et cartes locaux ne sont pas transférés. Les données locales restent sur ce téléphone. L’import ne peut être effectué qu’une fois par compte en ligne.")
            .setNegativeButton("Annuler",null).setPositiveButton("Importer",(d,w)->task(()->{
                api.importLocalCharacters(old,data);load();
                for(int i=0;i<data.length();i++){long legacy=data.getJSONObject(i).getLong("legacy_id");boolean copied=false;
                    for(int j=0;j<characters.length();j++)if(characters.getJSONObject(j).optLong("legacy_id",-1)==legacy){copied=true;break;}
                    if(!copied)throw new IllegalStateException("Vérification incomplète : garde tes données locales et contacte le MJ.");
                }
            },()->{notice(data.length()+" personnage(s) transféré(s) et vérifié(s).");render();})).show();}
    private void characterTabs(){LinearLayout row=new LinearLayout(this);body.addView(row);Button mine=button(row,"Mes personnages",()->{page="Personnages";selectedArchetype=-1;render();});Button classes=button(row,"Archétypes",()->{page="Archétypes";selectedArchetype=-1;render();});
        row.removeView(mine);row.removeView(classes);LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(48),1),right=new LinearLayout.LayoutParams(0,dp(48),1);left.rightMargin=dp(5);right.leftMargin=dp(5);
        mine.setBackground(TavernUi.background(this,"Personnages".equals(page)?0xff8f6b2e:0xff275340,9));classes.setBackground(TavernUi.background(this,"Archétypes".equals(page)?0xff8f6b2e:0xff275340,9));row.addView(mine,left);row.addView(classes,right);gap(body,16);}
    private void characterList(){
        body.addView(txt("Personnages",38,0xffe8e1d0));gap(body,24);characterTabs();
        LinearLayout p=card();p.addView(txt("Nouveau personnage",21,ink));EditText n=field(p,"Nom","",1);
        String[] archetypes=new String[ArchetypeRules.ALL.length];for(int i=0;i<archetypes.length;i++)archetypes[i]=ArchetypeRules.ALL[i].name;
        Spinner a=newCharacterChoice(p,"Archétype de classe",archetypes,archetypes),r=newCharacterChoice(p,"Race",RACES,new String[]{"Humain · Intelligence +10","Orc · Force +10","Nain · Endurance +10","Elfe · Agilité +10"}),
            o=newCharacterChoice(p,"Origine",ORIGINS,new String[]{"Citadin · Sociabilité +10","Reclu · Force mentale +10","Vie sauvage · Perception +10"});
        TextView originDetail=txt("Sélectionne une origine pour voir sa description.",14,0xff526056);p.addView(originDetail);
        o.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){originDetail.setText(position==1?"A grandi en ville, au milieu des métiers, des marchés et des intrigues.":position==2?"A vécu à l’écart, seul ou dans une communauté isolée.":position==3?"A grandi loin des cités, au contact de la nature et de ses dangers.":"Sélectionne une origine pour voir sa description.");}public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        button(p,"Créer",()->{if(n.getText().toString().trim().isEmpty()){notice("Indique un nom.");return;}if(a.getSelectedItemPosition()==0||r.getSelectedItemPosition()==0||o.getSelectedItemPosition()==0){notice("Choisis un archétype, une race et une origine.");return;}
            submit(()->{JSONObject saved=api.rpc("save_character",characterArgs(null,n.getText().toString(),chosenCharacterValue(r),chosenCharacterValue(a),chosenCharacterValue(o),"","","",""));selectedCharacter=saved.optString("value").replace("\"","");});});
        for(int i=0;i<characters.length();i++){JSONObject c=characters.optJSONObject(i);LinearLayout item=card();item.addView(txt(c.optString("name"),23,ink));item.addView(txt(c.optString("race")+" · "+c.optString("archetype")+" · "+c.optString("origin"),15,ink));button(item,"Ouvrir la fiche",()->{selectedCharacter=c.optString("id");render();});}
    }
    private JSONObject characterArgs(String id,String n,String race,String archetype,String origin,String sheet,String lore,String inventory,String notes)throws Exception{
        return new JSONObject().put("p_id",id==null?JSONObject.NULL:id).put("p_name",n).put("p_race",race).put("p_archetype",archetype)
            .put("p_origin",origin).put("p_sheet",sheet).put("p_lore",lore).put("p_inventory",inventory).put("p_notes",notes);
    }
    private void characterDetail(){JSONObject c=find(characters,selectedCharacter);if(c==null){selectedCharacter=null;characterList();return;}
        body.addView(txt(c.optString("name"),30,gold));button(body,"Retour aux personnages",()->{selectedCharacter=null;render();});
        LinearLayout stats=card();TavernUi.profile(this,stats,ArchetypeRules.forName(c.optString("archetype")),c.optString("race"),c.optString("origin"));
        LinearLayout p=card();p.addView(txt("Fiche du personnage",23,ink));EditText n=field(p,"Nom",c.optString("name"),1);
        String[] types=new String[ArchetypeRules.ALL.length];for(int i=0;i<types.length;i++)types[i]=ArchetypeRules.ALL[i].name;
        Spinner a=spinner(p,"Archétype",types,c.optString("archetype")),r=spinner(p,"Race",RACES,c.optString("race")),o=spinner(p,"Origine",ORIGINS,c.optString("origin"));
        EditText sheet=field(p,"Fiche et notes",c.optString("sheet"),5);
        LinearLayout belongings=card();belongings.addView(txt("Inventaire",23,ink));EditText inventory=field(belongings,"Objets et équipement",c.optString("inventory"),5);
        LinearLayout purse=card();purse.addView(txt("Bourse",23,ink));purse.addView(txt(c.optLong("gold")+" pièces d’or",17,ink));
        button(purse,"Renseigner la bourse",()->{EditText amount=new EditText(this);amount.setInputType(InputType.TYPE_CLASS_NUMBER);amount.setText(Long.toString(c.optLong("gold")));new AlertDialog.Builder(this).setTitle("Pièces d’or").setView(amount).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{try{long gold=Long.parseLong(amount.getText().toString());submit(()->api.rpc("set_character_gold",new JSONObject().put("p_id",c.optString("id")).put("p_gold",gold)));}catch(NumberFormatException e){notice("Nombre invalide.");}}).show();});
        LinearLayout story=card();story.addView(txt("Son histoire",23,ink));EditText lore=field(story,"Histoire",c.optString("lore"),5);
        LinearLayout campaignNotes=card();campaignNotes.addView(txt("Notes de campagne",23,ink));EditText notes=field(campaignNotes,"Notes",c.optString("campaign_notes"),4);
        LinearLayout actions=card();button(actions,"Enregistrer le personnage",()->{if(n.getText().toString().trim().isEmpty()){notice("Indique un nom.");return;}
            submit(()->api.rpc("save_character",characterArgs(c.optString("id"),n.getText().toString(),r.getSelectedItem().toString(),a.getSelectedItem().toString(),o.getSelectedItem().toString(),sheet.getText().toString(),lore.getText().toString(),inventory.getText().toString(),notes.getText().toString())));
        });
        button(actions,"Supprimer ce personnage",()->new AlertDialog.Builder(this).setTitle("Supprimer ce personnage ?").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->submit(()->{api.remove("characters",c.optString("id"));selectedCharacter=null;})).show());
        LinearLayout attachment=card();attachment.addView(txt("Anciennes pièces jointes",18,ink));attachment.addView(txt("Les fichiers locaux restent accessibles dans l’ancienne interface sur ce téléphone. Leur transfert en ligne n’est pas encore disponible.",14,ink));
    }
    private void shop(){body.addView(txt("Boutique",38,0xffe8e1d0));gap(body,24);if(characters.length()==0){LinearLayout empty=card();empty.addView(txt("Crée un personnage pour acheter des consommables.",17,ink));gap(empty,12);button(empty,"Créer un personnage",()->{page="Personnages";render();});return;}
        LinearLayout p=card();String[] labels=new String[characters.length()];for(int i=0;i<labels.length;i++)labels[i]=characters.optJSONObject(i).optString("name");
        Spinner target=spinner(p,"Personnage qui reçoit les achats",labels,"");TextView balance=txt("",17,ink);p.addView(balance);
        target.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){JSONObject chosen=characters.optJSONObject(position);balance.setText("Bourse : "+chosen.optLong("gold")+" pièces d’or");}public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        gap(p,8);p.addView(txt("Tu peux renseigner la bourse sur la fiche du personnage.",14,ink));
        for(int i=0;i<catalog.length();i++){JSONObject item=catalog.optJSONObject(i);LinearLayout row=card();row.addView(txt(item.optString("name"),22,ink));gap(row,5);row.addView(txt(item.optString("description"),15,ink));gap(row,7);row.addView(txt(item.optInt("price")+" pièces d’or",17,ink));gap(row,12);
            button(row,"Acheter pour "+item.optInt("price")+" pièces d’or",()->{JSONObject c=characters.optJSONObject(target.getSelectedItemPosition());new AlertDialog.Builder(this).setTitle("Acheter "+item.optString("name")+" ?")
                .setMessage("Pour "+c.optString("name")+" · "+item.optInt("price")+" pièces d’or. L’objet ira dans son inventaire.").setNegativeButton("Annuler",null)
                .setPositiveButton("Acheter",(d,w)->submit(()->api.buy(c.optString("id"),item.optString("id")))).show();});}
    }
    private List<JSONObject> contractSlots(String contractId){List<JSONObject> out=new ArrayList<>();for(int i=0;i<slots.length();i++){JSONObject s=slots.optJSONObject(i);if(contractId.equals(s.optString("contract_id")))out.add(s);}out.sort((a,b)->Integer.compare(a.optInt("weekday"),b.optInt("weekday")));return out;}
    private String slotText(JSONObject s){return DAYS[s.optInt("weekday")]+" · "+clock(s.optInt("start_minute"))+" – "+clock(s.optInt("end_minute"))+(s.optInt("end_minute")<s.optInt("start_minute")?" (lendemain)":"");}
    private String clock(int mins){return String.format(java.util.Locale.FRANCE,"%02d:%02d",mins/60,mins%60);}
    private int count(JSONArray a,String key,String value){int n=0;for(int i=0;i<a.length();i++)if(value.equals(a.optJSONObject(i).optString(key)))n++;return n;}
    private void contractList(){body.addView(txt("Contrats",38,0xffe8e1d0));gap(body,24);
        LinearLayout tabs=new LinearLayout(this);body.addView(tabs);gap(body,18);
        Button active=button(tabs,"En cours",()->{completedContracts=false;render();});Button completed=button(tabs,"Contrats terminés",()->{completedContracts=true;render();});tabs.removeView(active);tabs.removeView(completed);tabs.addView(active,new LinearLayout.LayoutParams(0,dp(48),1));tabs.addView(completed,new LinearLayout.LayoutParams(0,dp(48),1));
        active.setEnabled(completedContracts);completed.setEnabled(!completedContracts);
        if(!completedContracts){LinearLayout publish=card();button(publish,"Proposer un contrat",()->contractForm(null));}
        boolean any=false;for(int i=0;i<contracts.length();i++){JSONObject c=contracts.optJSONObject(i);if("terminé".equals(c.optString("status"))!=completedContracts)continue;any=true;LinearLayout p=card();
            p.addView(txt(c.optString("status").toUpperCase(java.util.Locale.FRANCE),12,0xff75572f));gap(p,12);p.addView(txt(c.optString("title"),27,ink));gap(p,6);
            p.addView(txt("Proposé par : "+player(c.optString("proposer_id")),14,0xff75572f));gap(p,10);p.addView(txt(c.optString("description"),16,0xff526056));gap(p,15);
            p.addView(txt(count(participants,"contract_id",c.optString("id"))+" / "+c.optInt("places")+" mercenaires  ·  Danger "+c.optInt("danger")+"/5",14,ink));gap(p,12);
            button(p,"Voir le contrat",()->{selectedContract=c.optString("id");render();});}
        if(!any)card().addView(txt(completedContracts?"Aucun contrat terminé":"Aucun contrat en cours",21,ink));
    }
    private void contractDetail(){JSONObject c=find(contracts,selectedContract);if(c==null){selectedContract=null;contractList();return;}
        button(body,"‹  Tous les contrats",()->{selectedContract=null;render();});gap(body,20);body.addView(txt(c.optString("title"),38,0xffe8e1d0));gap(body,24);
        LinearLayout p=card();p.addView(txt(c.optString("status").toUpperCase(java.util.Locale.FRANCE)+"  ·  Danger "+c.optInt("danger")+"/5",13,0xff75572f));gap(p,16);
        p.addView(txt(c.optString("description"),18,ink));gap(p,10);p.addView(txt("Proposé par : "+player(c.optString("proposer_id")),14,0xff75572f));gap(p,14);
        p.addView(txt("Récompense · "+c.optLong("reward_gold")+" po",15,0xff75572f));
        List<JSONObject> proposed=contractSlots(c.optString("id"));LinearLayout schedule=card();schedule.addView(txt("Disponibilités du MJ",23,ink));gap(schedule,10);
        for(JSONObject slot:proposed){int n=count(votes,"slot_id",slot.optString("id"));String date=!c.isNull("locked_date")&&slot.optString("id").equals(c.optString("locked_slot_id"))?" · "+c.optString("locked_date"):"";
            schedule.addView(txt(slotText(slot)+date+" · "+n+" vote"+(n>1?"s":""),16,ink));List<String> voters=new ArrayList<>();
            for(int i=0;i<votes.length();i++){JSONObject vote=votes.optJSONObject(i);if(slot.optString("id").equals(vote.optString("slot_id")))voters.add(player(owner(vote.optString("character_id"))));}
            schedule.addView(txt(voters.isEmpty()?"Aucun votant":android.text.TextUtils.join(", ",voters),14,0xff526056));gap(schedule,10);}
        LinearLayout group=card();group.addView(txt("La compagnie",22,ink));for(int i=0;i<participants.length();i++){JSONObject member=participants.optJSONObject(i);if(c.optString("id").equals(member.optString("contract_id")))group.addView(txt("• "+name(member.optString("character_id")),16,ink));}
        if("ouvert".equals(c.optString("status"))&&characters.length()>0){String[] choices=new String[characters.length()];for(int i=0;i<choices.length;i++)choices[i]=characters.optJSONObject(i).optString("name");
            Spinner ch=spinner(group,"Ton personnage",choices,"");List<CheckBox> boxes=new ArrayList<>();for(JSONObject slot:proposed){CheckBox box=new CheckBox(this);box.setText(slotText(slot));box.setTextColor(ink);group.addView(box);boxes.add(box);}
            Runnable restoreVotes=()->{JSONObject chosen=characters.optJSONObject(ch.getSelectedItemPosition());if(chosen==null)return;
                for(int j=0;j<boxes.size();j++){String slotId=proposed.get(j).optString("id");boolean checked=false;
                    for(int k=0;k<votes.length();k++){JSONObject vote=votes.optJSONObject(k);if(slotId.equals(vote.optString("slot_id"))&&chosen.optString("id").equals(vote.optString("character_id"))){checked=true;break;}}
                    boxes.get(j).setChecked(checked);}};
            ch.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){restoreVotes.run();}public void onNothingSelected(android.widget.AdapterView<?> parent){}});
            restoreVotes.run();for(CheckBox box:boxes)box.setOnCheckedChangeListener((view,checked)->draftDirty=true);
            button(group,"Inscrire et voter",()->{JSONObject chosen=characters.optJSONObject(ch.getSelectedItemPosition());JSONArray selected=new JSONArray();for(int i=0;i<boxes.size();i++)if(boxes.get(i).isChecked())selected.put(proposed.get(i).optString("id"));if(selected.length()==0&&boxes.size()>0){notice("Choisis au moins un créneau.");return;}
                submit(()->api.joinAndVote(c.optString("id"),chosen.optString("id"),selected));});}
        LinearLayout chat=card();chat.addView(txt("À la table",22,ink));for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(c.optString("id").equals(m.optString("contract_id")))chat.addView(txt(name(m.optString("character_id"))+" : "+m.optString("body"),15,ink));}
        if(characters.length()>0){String[] choices=new String[characters.length()];for(int i=0;i<choices.length;i++)choices[i]=characters.optJSONObject(i).optString("name");Spinner ch=spinner(chat,"Personnage inscrit",choices,"");EditText msg=field(chat,"Message","",2);
            button(chat,"Envoyer",()->{String body=msg.getText().toString().trim();if(body.isEmpty())return;JSONObject chosen=characters.optJSONObject(ch.getSelectedItemPosition());submit(()->api.insertMessage(new JSONObject().put("contract_id",c.optString("id")).put("character_id",chosen.optString("id")).put("body",body)));});}
        if(userId.equals(c.optString("proposer_id"))){LinearLayout mj=card();mj.addView(txt("Gestion MJ",22,ink));if("ouvert".equals(c.optString("status")))button(mj,"Modifier le contrat",()->contractForm(c));
            if("ouvert".equals(c.optString("status"))&&!proposed.isEmpty())for(JSONObject slot:proposed)button(mj,"Bloquer une date · "+slotText(slot),()->pickDate(c,slot));
            if("planifié".equals(c.optString("status")))button(mj,"Déclarer terminé et partager la prime",()->new AlertDialog.Builder(this).setTitle("Terminer le contrat ?")
                .setMessage("La récompense sera répartie entre les personnages inscrits. Cette opération n’a lieu qu’une fois.").setNegativeButton("Annuler",null)
                .setPositiveButton("Terminer",(d,w)->submit(()->api.completeContract(c.optString("id")))).show());
            if("ouvert".equals(c.optString("status")))button(mj,"Supprimer le contrat",()->new AlertDialog.Builder(this).setTitle("Supprimer le contrat ?").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->submit(()->{api.remove("contracts",c.optString("id"));selectedContract=null;})).show());}
    }
    private void pickDate(JSONObject contract,JSONObject slot){LocalDate first=LocalDate.now();while(first.getDayOfWeek().getValue()-1!=slot.optInt("weekday"))first=first.plusDays(1);LocalDate initial=first;
        DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,d)->{LocalDate date=LocalDate.of(y,m+1,d);if(date.getDayOfWeek().getValue()-1!=slot.optInt("weekday")){notice("Choisis un "+DAYS[slot.optInt("weekday")]+".");return;}
            new AlertDialog.Builder(this).setTitle("Bloquer cette date ?").setMessage(date+" · "+slotText(slot)).setNegativeButton("Annuler",null)
                .setPositiveButton("Bloquer",(dialog,which)->submit(()->api.lockDate(contract.optString("id"),slot.optString("id"),date.toString()))).show();},initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth());
        picker.getDatePicker().setMinDate(System.currentTimeMillis()-60000);picker.show();}
    private void contractForm(JSONObject current){editingContractId=current==null?null:current.optString("id");pendingContractId=current==null?UUID.randomUUID().toString():null;contractEditor=true;render();}
    private void contractFormScreen(){JSONObject current=editingContractId==null?null:find(contracts,editingContractId);
        body.addView(txt(current==null?"Publier contrat":"Modifier le contrat",38,0xffe8e1d0));gap(body,24);
        button(body,"‹  Retour à l’Espace MJ",()->{contractEditor=false;render();});gap(body,18);
        LinearLayout form=card();form.addView(txt(current==null?"Nouveau contrat":"Modifier le contrat",23,ink));gap(form,16);
        EditText title=field(form,"Titre",current==null?"":current.optString("title"),1),description=field(form,"Description",current==null?"":current.optString("description"),4),reward=field(form,"Récompense en pièces d’or",current==null?"":Long.toString(current.optLong("reward_gold")),1);
        reward.setInputType(InputType.TYPE_CLASS_NUMBER);EditText danger=field(form,"Danger (1 à 5)",current==null?"1":Integer.toString(current.optInt("danger")),1),places=field(form,"Places (1 à 12)",current==null?"4":Integer.toString(current.optInt("places")),1);danger.setInputType(InputType.TYPE_CLASS_NUMBER);places.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(txt("Disponibilités du MJ · coche plusieurs jours",16,ink));CheckBox[] days=new CheckBox[7];List<JSONObject> existing=current==null?new ArrayList<>():contractSlots(current.optString("id"));for(int i=0;i<7;i++){CheckBox cb=new CheckBox(this);cb.setText(DAYS[i]);cb.setTextColor(ink);for(JSONObject s:existing)if(s.optInt("weekday")==i)cb.setChecked(true);form.addView(cb);days[i]=cb;}gap(form,12);
        form.addView(txt("Plage horaire commune aux jours cochés",14,0xff526056));gap(form,10);
        int[] times={existing.isEmpty()?19*60:existing.get(0).optInt("start_minute"),existing.isEmpty()?23*60:existing.get(0).optInt("end_minute")};
        Button start=button(form,"Début · "+clock(times[0]),()->{}),end=button(form,"Fin · "+clock(times[1]),()->{});
        start.setOnClickListener(v->new TimePickerDialog(this,(p,h,m)->{times[0]=h*60+m;start.setText("Début · "+clock(times[0]));},times[0]/60,times[0]%60,true).show());
        end.setOnClickListener(v->new TimePickerDialog(this,(p,h,m)->{times[1]=h*60+m;end.setText("Fin · "+clock(times[1]));},times[1]/60,times[1]%60,true).show());
        float[] point={current==null?-1f:(float)current.optDouble("map_x",-1),current==null?-1f:(float)current.optDouble("map_y",-1)};
        Button place=button(form,point[0]<0?"Placer le contrat sur la carte":"Déplacer le contrat sur la carte",()->{});
        place.setOnClickListener(v->chooseMap(point,()->place.setText("Lieu choisi ✓ · toucher pour modifier")));
        button(form,current==null?"Afficher le contrat":"Enregistrer les modifications",()->{JSONArray chosen=new JSONArray();for(int i=0;i<days.length;i++)if(days[i].isChecked())chosen.put(i);if(chosen.length()==0){notice("Choisis un jour.");return;}
                if(title.getText().toString().trim().isEmpty()||description.getText().toString().trim().isEmpty()){notice("Titre et description nécessaires.");return;}
                if(times[0]==times[1]){notice("Choisis une heure de fin différente.");return;}
                if(point[0]<0){notice("Place le contrat sur la carte.");return;}
                try{long amount=Long.parseLong(reward.getText().toString());int risk=Integer.parseInt(danger.getText().toString()),capacity=Integer.parseInt(places.getText().toString());
                    JSONObject args=new JSONObject().put("p_title",title.getText().toString()).put("p_description",description.getText().toString()).put("p_reward",amount)
                        .put("p_danger",risk).put("p_places",capacity).put("p_x",point[0]).put("p_y",point[1]).put("p_days",chosen).put("p_start",times[0]).put("p_end",times[1]);
                    args.put("p_id",current==null?pendingContractId:current.optString("id"));task(()->{api.rpc(current==null?"publish_contract_once":"edit_contract",args);load();},()->{contractEditor=false;pendingContractId=null;render();});
                }catch(Exception e){notice("Récompense, danger ou places invalides.");}
            });
        if(current!=null)button(form,"Supprimer ce contrat",()->new AlertDialog.Builder(this).setTitle("Supprimer ce contrat ?")
            .setMessage("Les inscriptions, votes et messages seront aussi supprimés.").setNegativeButton("Annuler",null)
            .setPositiveButton("Supprimer",(d,w)->task(()->{api.remove("contracts",current.optString("id"));load();},()->{contractEditor=false;selectedContract=null;render();})).show());
    }
    private void chooseMap(float[] point){chooseMap(point,()->{});}
    private void chooseMap(float[] point,Runnable chosen){MapView map=new MapView(this,true,null);map.setChosen(point[0],point[1]);LinearLayout holder=col();holder.addView(map,new LinearLayout.LayoutParams(-1,dp(450)));
        new AlertDialog.Builder(this).setTitle("Emplacement du contrat").setView(holder)
        .setNegativeButton("Annuler",null).setPositiveButton("Choisir",(d,w)->{if(map.chosenX()>=0){point[0]=map.chosenX();point[1]=map.chosenY();chosen.run();}}).show();}
    private void rumourList(){body.addView(txt("Rumeurs",38,0xffe8e1d0));gap(body,24);boolean any=false;
        for(int i=0;i<rumours.length();i++){JSONObject r=rumours.optJSONObject(i);any=true;LinearLayout p=card();p.addView(txt(r.optString("description"),18,ink));gap(p,12);
            button(p,"Voir sur la carte",()->{page="Carte";render();});}
        if(!any)card().addView(txt("Aucune rumeur pour le moment.",17,ink));
    }
    private void rumourEditor(){body.addView(txt("Placer une rumeur",38,0xffe8e1d0));gap(body,24);button(body,"‹  Retour à l’Espace MJ",()->{page="Espace MJ";render();});gap(body,18);
        LinearLayout create=card();create.addView(txt("Placer une rumeur",23,ink));gap(create,10);create.addView(txt("Un indice discret pour donner envie d’explorer, sans danger ni récompense.",15,0xff526056));gap(create,12);
        EditText description=field(create,"Indice","",3);float[] point={-1f,-1f};Button place=button(create,"Placer la rumeur sur la carte",()->{});
        place.setOnClickListener(v->chooseMap(point,()->place.setText("Lieu choisi ✓ · toucher pour modifier")));
        button(create,"Publier la rumeur",()->{String value=description.getText().toString().trim();if(value.isEmpty()){notice("Ajoute une courte description.");return;}
            if(point[0]<0){notice("Choisis un lieu sur la carte.");return;}if(pendingRumourId==null)pendingRumourId=UUID.randomUUID().toString();String id=pendingRumourId;
            task(()->{api.insertRumourOnce(new JSONObject().put("id",id).put("owner_id",userId).put("description",value).put("map_x",point[0]).put("map_y",point[1]));load();},()->{pendingRumourId=null;page="Mes rumeurs";render();});});
    }
    private void myRumours(){body.addView(txt("Mes rumeurs",38,0xffe8e1d0));gap(body,24);button(body,"‹  Retour à l’Espace MJ",()->{page="Espace MJ";render();});gap(body,18);boolean any=false;
        for(int i=0;i<rumours.length();i++){JSONObject r=rumours.optJSONObject(i);if(!userId.equals(r.optString("owner_id")))continue;any=true;LinearLayout p=card();p.addView(txt(r.optString("description"),17,ink));gap(p,12);
            button(p,"Modifier",()->{LinearLayout form=col();form.setPadding(dp(16),dp(8),dp(16),dp(8));EditText text=field(form,"Rumeur",r.optString("description"),3);float[] location={(float)r.optDouble("map_x"),(float)r.optDouble("map_y")};button(form,"Déplacer sur la carte",()->chooseMap(location));
                new AlertDialog.Builder(this).setTitle("Modifier la rumeur").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->submit(()->api.rpc("edit_rumour",new JSONObject().put("p_id",r.optString("id")).put("p_description",text.getText().toString()).put("p_x",location[0]).put("p_y",location[1])))).show();});
            button(p,"Supprimer",()->new AlertDialog.Builder(this).setTitle("Supprimer cette rumeur ?")
                .setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->submit(()->api.remove("rumours",r.optString("id")))).show());}
        if(!any)card().addView(txt("Aucune rumeur publiée",22,ink));
    }
    private void mapScreen(){List<MapView.Marker> markers=new ArrayList<>();Map<Long,String> details=new HashMap<>(),contractIds=new HashMap<>();long id=1;
        for(int i=0;i<contracts.length();i++){JSONObject c=contracts.optJSONObject(i);if(c.isNull("map_x")||c.isNull("map_y")||"terminé".equals(c.optString("status")))continue;markers.add(new MapView.Marker(id,false,(float)c.optDouble("map_x"),(float)c.optDouble("map_y")));details.put(id,c.optString("title"));contractIds.put(id,c.optString("id"));id++;}
        for(int i=0;i<rumours.length();i++){JSONObject r=rumours.optJSONObject(i);markers.add(new MapView.Marker(id,true,(float)r.optDouble("map_x"),(float)r.optDouble("map_y")));details.put(id,r.optString("description"));id++;}
        MapView map=new MapView(this,false,m->{if(!m.interest){page="Contrats";selectedContract=contractIds.get(m.id);render();}
            else new AlertDialog.Builder(this).setTitle("Rumeur").setMessage(details.get(m.id)).setPositiveButton("Fermer",null).show();});map.focusOnTavern();map.setMarkers(markers);root.addView(map,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=txt("Pince pour zoomer · déplace la carte · touche un repère",13,0xffeedfbb);hint.setGravity(Gravity.CENTER);hint.setPadding(dp(8),dp(8),dp(8),dp(8));root.addView(hint);
    }
    private void archetypes(){int[] pictures={R.drawable.archetype_guerrier,R.drawable.archetype_mage,R.drawable.archetype_archer,R.drawable.archetype_roublard,R.drawable.archetype_barbare,R.drawable.archetype_enqueteur,R.drawable.archetype_explorateur,R.drawable.archetype_paladin,R.drawable.archetype_assassin,R.drawable.archetype_chasseur};
        if(selectedArchetype>=0){int index=selectedArchetype;button(body,"‹  Tous les archétypes",()->{selectedArchetype=-1;render();});gap(body,18);body.addView(txt(ArchetypeRules.ALL[index].name,38,0xffe8e1d0));gap(body,24);
            LinearLayout portrait=card();ImageView image=new ImageView(this);image.setImageResource(pictures[index]);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundColor(0xfff8f0df);portrait.addView(image,new LinearLayout.LayoutParams(-1,dp(320)));
            gap(portrait,14);portrait.addView(txt(TavernUi.ARCHETYPE_DESCRIPTIONS[index],17,ink));LinearLayout rules=card();TavernUi.profile(this,rules,ArchetypeRules.ALL[index],"","");return;}
        body.addView(txt("Archétypes",38,0xffe8e1d0));gap(body,24);characterTabs();
        for(int i=0;i<ArchetypeRules.ALL.length;i++){int index=i;ArchetypeRules.Profile profile=ArchetypeRules.ALL[i];LinearLayout p=card();p.addView(txt(profile.name,23,ink));gap(p,8);p.addView(txt(TavernUi.ARCHETYPE_DESCRIPTIONS[i],16,ink));gap(p,12);button(p,"Voir la fiche  ›",()->{selectedArchetype=index;render();});}
    }
    private void dice(){body.addView(txt("Lancer les dés",30,gold));java.security.SecureRandom random=new java.security.SecureRandom();for(int sides:new int[]{4,6,8,10,12,20,100}){
            LinearLayout p=card();p.addView(txt("D"+sides,23,ink));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);p.addView(row);
            final int[] count={1};TextView number=txt("1",23,ink);button(row,"‹",()->{count[0]=Math.max(1,count[0]-1);number.setText(Integer.toString(count[0]));});row.addView(number);
            button(row,"›",()->{count[0]=Math.min(100,count[0]+1);number.setText(Integer.toString(count[0]));});TextView result=txt("",17,ink);p.addView(result);
            button(p,"Lancer "+sides+" faces",()->{StringBuilder values=new StringBuilder();int sum=0;for(int i=0;i<count[0];i++){int die=random.nextInt(sides)+1;sum+=die;if(i>0)values.append(" + ");values.append(die);}result.setText(values+" = "+sum);});}
    }
    @Override public void onBackPressed(){if(contractEditor){contractEditor=false;render();}else if(selectedCharacter!=null){selectedCharacter=null;render();}else if(selectedContract!=null){selectedContract=null;render();}else if(selectedArchetype>=0){selectedArchetype=-1;render();}else if(selectedCampaignCharacter!=null){selectedCampaignCharacter=null;expandedCampaignPanel=-1;render();}else if("Placer une rumeur".equals(page)||"Mes rumeurs".equals(page)){page="Espace MJ";render();}else finish();}
}
