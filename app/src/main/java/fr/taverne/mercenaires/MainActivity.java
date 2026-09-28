package fr.taverne.mercenaires;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Matrix;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.ImageView;
import android.text.InputType;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import android.widget.AdapterView;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String[] WEEKDAYS={"Lundi","Mardi","Mercredi","Jeudi","Vendredi","Samedi","Dimanche"};
    private static final String[] ARCHETYPES={"Guerrier","Mage","Archer","Roublard","Barbare","Enquêteur","Explorateur","Paladin","Assassin","Chasseur"};
    private static final String[] ARCHETYPE_DESCRIPTIONS={
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
    private static final String[] RACES={"Humain","Orc","Nain","Elfe"};
    private static final String[] ORIGINS={"Citadin","Reclu","Vie sauvage"};
    private static final String[] ORIGIN_DESCRIPTIONS={
        "A grandi en ville, au milieu des métiers, des marchés et des intrigues.",
        "A vécu à l’écart, seul ou dans une communauté isolée.",
        "A grandi loin des cités, au contact de la nature et de ses dangers."
    };
    private static final int FOREST=Color.rgb(20,35,31), PAPER=Color.rgb(234,226,206), GOLD=Color.rgb(216,183,109), INK=Color.rgb(37,53,46);
    private static final int PICK_SHEET=41, PICK_MAP=42;
    private static final String[] MJ_TABS={"Publier contrat","Mes contrats","Placer une rumeur","Mes rumeurs","Cartes","Mes cartes","Bestiaire"};
    private TavernDb db;
    private LinearLayout body,root;
    private ScrollView pageScroll;
    private String renderedPage;
    private final Map<String,Integer> scrollPositions=new HashMap<>();
    private String section="accueil";
    private int charactersPage=0,mjPage=-1;
    private int openArchetype=-1;
    private static final int[] ARCHETYPE_IMAGES={R.drawable.archetype_guerrier,R.drawable.archetype_mage,R.drawable.archetype_archer,R.drawable.archetype_roublard,R.drawable.archetype_barbare,R.drawable.archetype_enqueteur,R.drawable.archetype_explorateur,R.drawable.archetype_paladin,R.drawable.archetype_assassin,R.drawable.archetype_chasseur};
    private long openContract=-1;
    private boolean completedContracts=false;
    private long editingContractId=-1;
    private long openCharacter=-1;
    private long campaignCharacter=-1;
    private int expandedCampaignPanel=-1;
    private long accountId=-1;
    private boolean creatingAccount=false;
    private UpdateManager updates;
    private MediaPlayer tavernAudio;
    private static final String AUDIO_PREFS="audio_settings";
    private static final String MUSIC_ENABLED="music_enabled";
    private static final String SESSION_PREFS="session";
    private static final String REMEMBERED_ACCOUNT="remembered_account_id";
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(FOREST);getWindow().setNavigationBarColor(FOREST);db=new TavernDb(this);SharedPreferences prefs=getSharedPreferences(SESSION_PREFS,MODE_PRIVATE);long remembered=prefs.getLong(REMEMBERED_ACCOUNT,-1);if(remembered>=0){if(!db.accountName(remembered).isEmpty())accountId=remembered;else prefs.edit().remove(REMEMBERED_ACCOUNT).apply();}updates=new UpdateManager(this);show();updates.check(false);}
    @Override protected void onResume(){super.onResume();if(root!=null)root.post(this::hideSystemBarsAfterAttach);if(updates!=null)updates.resume();syncTavernAudio();}
    @Override protected void onPause(){pauseTavernAudio();super.onPause();}
    @Override protected void onDestroy(){if(tavernAudio!=null){tavernAudio.release();tavernAudio=null;}super.onDestroy();}
    private boolean musicEnabled(){return getSharedPreferences(AUDIO_PREFS,MODE_PRIVATE).getBoolean(MUSIC_ENABLED,true);}
    private void pauseTavernAudio(){if(tavernAudio!=null&&tavernAudio.isPlaying())tavernAudio.pause();}
    private void syncTavernAudio(){
        if(accountId<0||!section.equals("accueil")||!musicEnabled()||isFinishing()){pauseTavernAudio();return;}
        if(tavernAudio==null){tavernAudio=MediaPlayer.create(this,R.raw.tavern_ambience);if(tavernAudio==null)return;tavernAudio.setLooping(true);}
        if(!tavernAudio.isPlaying())tavernAudio.start();
    }
    private void hideSystemBarsAfterAttach(){
        if(root==null||!root.isAttachedToWindow())return;
        View decor=getWindow().getDecorView();
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=decor.getWindowInsetsController();
            if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.systemBars());}
        }else decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        root.requestApplyInsets();
    }
    @Override public void onBackPressed(){if(section.equals("personnages")&&openArchetype>=0){openArchetype=-1;show();}else if(section.equals("campagne")&&campaignCharacter>=0){campaignCharacter=-1;expandedCampaignPanel=-1;show();}else if(openCharacter>=0||openContract>=0){openCharacter=-1;openContract=-1;show();}else if(section.equals("mj")&&mjPage>=0){mjPage=-1;editingContractId=-1;show();}else if(accountId>=0&&!section.equals("accueil")){enterSection("accueil");}else super.onBackPressed();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==PICK_SHEET&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null&&openCharacter>=0&&accountId>=0){Uri uri=data.getData();getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);db.setFileUri(accountId,openCharacter,uri.toString());show();}else if(requestCode==PICK_MAP&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null&&accountId>=0){Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);String name=uri.getLastPathSegment();try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}db.addDungeonMap(accountId,name==null?"Carte":name,uri.toString());mjPage=5;show();}catch(Exception e){info("Impossible d’ajouter cette carte.");}}}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("serif",Typeface.BOLD));return t;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private void gap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private void label(LinearLayout parent,String value){TextView t=text(value,16,INK,true);parent.addView(t);gap(parent,6);}
    private EditText input(LinearLayout parent,String hint,String value,int minLines){EditText e=new EditText(this);e.setSingleLine(minLines<=1);e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | (minLines>1 ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));e.setHint(hint);e.setText(value);e.setTextColor(INK);e.setHintTextColor(0xff87918a);e.setTextSize(16);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(background(0xfffbfaf4,8));if(minLines>1){e.setMinLines(minLines);e.setGravity(Gravity.TOP);}parent.addView(e,new LinearLayout.LayoutParams(-1,-2));gap(parent,16);return e;}
    private Button button(String value,Runnable action){Button b=new Button(this);b.setText(value);b.setTextSize(15);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(background(0xff275340,9));b.setOnClickListener(v->action.run());return b;}
    private LinearLayout panel(){LinearLayout p=column();p.setPadding(dp(20),dp(20),dp(20),dp(20));p.setBackground(background(PAPER,14));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(16);body.addView(p,params);return p;}
    private void title(String value){TextView eyebrow=text("COMPAGNIE DES MERCENAIRES",12,GOLD,true);eyebrow.setLetterSpacing(.12f);body.addView(eyebrow);gap(body,9);TextView h=text(value,38,0xffe8e1d0,true);body.addView(h);gap(body,24);}
    private void info(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void enterSection(String target){section=target;show();}
    private String pageKey(){
        return accountId+":"+section+":"+charactersPage+":"+openArchetype+":"+openCharacter+":"+openContract+":"+mjPage+":"+campaignCharacter+":"+expandedCampaignPanel;
    }
    private void show(){
        if(pageScroll!=null&&renderedPage!=null)scrollPositions.put(renderedPage,pageScroll.getScrollY());
        pageScroll=null;
        renderedPage=pageKey();
        root=column();root.setBackgroundColor(FOREST);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());top=bars.top;bottom=bars.bottom;}
            else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            view.setPadding(0,top,0,bottom);
            return insets;
        });
        setContentView(root);root.requestApplyInsets();root.post(this::hideSystemBarsAfterAttach);syncTavernAudio();
        if(accountId<0){showAuthentication();return;}
        if(section.equals("accueil"))showHome();
        else{
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(14),dp(18),dp(12));root.addView(header);
        TextView back=text("‹",32,GOLD,false);back.setGravity(Gravity.CENTER);back.setContentDescription("Retour à la taverne");header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->{openCharacter=-1;openContract=-1;editingContractId=-1;campaignCharacter=-1;enterSection("accueil");});
        TextView brand=text("La Taverne",24,0xffe8e1d0,true);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));TextView gear=gearMenu();header.addView(gear,new LinearLayout.LayoutParams(dp(52),dp(52)));
        if(section.equals("carte")){showMap();}
        else{
        ScrollView scroll=new ScrollView(this);pageScroll=scroll;root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=column();body.setPadding(dp(16),dp(24),dp(16),dp(28));scroll.addView(body);
        if(section.equals("contrats")){if(openContract<0)contractList();else contractDetail();}
        else if(section.equals("rumeurs"))rumoursScreen();
        else if(section.equals("personnages")){if(charactersPage==1){if(openArchetype>=0)archetypeDetail(openArchetype);else archetypeScreen();}else if(openCharacter<0)characterList();else characterDetail();}
        else if(section.equals("mj"))masterScreen();
        else if(section.equals("campagne"))campaignScreen();
        else if(section.equals("boutique"))placeholderScreen("Boutique");
        Integer savedPosition=scrollPositions.get(renderedPage);
        if(savedPosition!=null)scroll.post(()->scroll.scrollTo(0,savedPosition));
        }
        }
        if(section.equals("campagne")&&expandedCampaignPanel>=0)return;
        HorizontalScrollView navScroll=new HorizontalScrollView(this);navScroll.setHorizontalScrollBarEnabled(false);navScroll.setFillViewport(true);navScroll.setBackgroundColor(0xff192a25);root.addView(navScroll,new LinearLayout.LayoutParams(-1,dp(68)));
        LinearLayout nav=new LinearLayout(this);navScroll.addView(nav,new android.widget.FrameLayout.LayoutParams(-2,-1));
        tab(nav,"Contrats","contrats");tab(nav,"Carte","carte");tab(nav,"Rumeurs","rumeurs");tab(nav,"Personnages","personnages");tab(nav,"Boutique","boutique");tab(nav,"Espace MJ","mj");
    }
    private TextView gearMenu(){
        TextView gear=text("⚙",27,0xffe8e1d0,false);gear.setGravity(Gravity.CENTER);gear.setBackground(background(0x90324a3c,30));gear.setContentDescription("Options de l’application");gear.setOnClickListener(v->{
            LinearLayout entries=column();entries.setBackground(background(0xb0324a3c,18));
            PopupWindow menu=new PopupWindow(entries,dp(52),dp(162),true);menu.setBackgroundDrawable(background(0xb0324a3c,18));menu.setElevation(dp(6));menu.setOutsideTouchable(true);
            TextView update=text("↻",27,GOLD,false);update.setGravity(Gravity.CENTER);update.setContentDescription("Vérifier les mises à jour");entries.addView(update,new LinearLayout.LayoutParams(dp(52),dp(54)));update.setOnClickListener(click->{menu.dismiss();updates.check(true);});
            TextView music=text(musicEnabled()?"♫":"♪",27,GOLD,false);music.setGravity(Gravity.CENTER);music.setContentDescription(musicEnabled()?"Désactiver la musique":"Activer la musique");entries.addView(music,new LinearLayout.LayoutParams(dp(52),dp(54)));music.setOnClickListener(click->{getSharedPreferences(AUDIO_PREFS,MODE_PRIVATE).edit().putBoolean(MUSIC_ENABLED,!musicEnabled()).apply();syncTavernAudio();menu.dismiss();});
            TextView logout=text("✕",27,0xffffb8a9,false);logout.setGravity(Gravity.CENTER);logout.setContentDescription("Quitter");entries.addView(logout,new LinearLayout.LayoutParams(dp(52),dp(54)));logout.setOnClickListener(click->{menu.dismiss();getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit().remove(REMEMBERED_ACCOUNT).apply();accountId=-1;openCharacter=-1;openContract=-1;section="accueil";show();});
            menu.showAsDropDown(gear,0,dp(4));
        });return gear;
    }
    private void showHome(){
        FrameLayout frame=new FrameLayout(this);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        frame.addView(new TavernHomeView(),new FrameLayout.LayoutParams(-1,-1));
        TextView gear=gearMenu();FrameLayout.LayoutParams corner=new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.TOP|Gravity.RIGHT);corner.setMargins(0,dp(12),dp(12),0);frame.addView(gear,corner);
        View campaign=new CampaignScroll();campaign.setElevation(dp(5));
        campaign.setContentDescription("Ouvrir la campagne");
        FrameLayout.LayoutParams link=new FrameLayout.LayoutParams(dp(190),dp(64),Gravity.BOTTOM|Gravity.RIGHT);
        link.setMargins(0,0,dp(16),dp(16));frame.addView(campaign,link);
        campaign.setOnClickListener(v->{campaignCharacter=-1;expandedCampaignPanel=-1;enterSection("campagne");});
    }
    private final class CampaignScroll extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        CampaignScroll(){super(MainActivity.this);setClickable(true);}
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),h=getHeight(),unit=w/190f;
            canvas.save();canvas.scale(unit,h/64f);
            Path parchment=new Path();parchment.moveTo(12,10);parchment.cubicTo(36,14,116,9,151,12);parchment.lineTo(184,31);parchment.lineTo(151,51);parchment.cubicTo(111,50,35,54,12,53);parchment.close();
            p.setStyle(Paint.Style.FILL);p.setColor(0xffd6b982);canvas.drawPath(parchment,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xff503a26);canvas.drawPath(parchment,p);
            p.setStyle(Paint.Style.FILL);p.setColor(0xffb9925f);canvas.drawOval(2,8,23,56,p);
            p.setColor(0xfff0dbac);canvas.drawOval(4,10,21,53,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(0xff78583b);canvas.drawOval(4,10,21,53,p);
            p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("serif",Typeface.BOLD));p.setTextSize(20);p.setTextAlign(Paint.Align.CENTER);p.setColor(0xff493321);canvas.drawText("Campagne",92,38,p);
            canvas.restore();
        }
    }
    private final class TavernHomeView extends View {
        private final Bitmap scene=BitmapFactory.decodeResource(getResources(),R.drawable.tavern_home);
        private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG),highlight=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float scale,left,top,downX,downY;
        TavernHomeView(){super(MainActivity.this);setContentDescription("Taverne : toucher le tavernier, la carte, le panneau ou le miroir");}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(scene==null)return;
            scale=Math.min(getWidth()/(float)scene.getWidth(),getHeight()/(float)scene.getHeight());left=(getWidth()-scene.getWidth()*scale)/2f;top=getHeight()-scene.getHeight()*scale;
            canvas.drawBitmap(scene,null,new android.graphics.RectF(left,top,left+scene.getWidth()*scale,top+scene.getHeight()*scale),paint);
            if(top>0)drawWoodSign(canvas,top);
            // Tracés relevés sur l'illustration de référence 864 × 1536. Les parties
            // masquées par les personnages ne sont pas reliées par une forme fictive.
            trace(canvas, 40,510, 45,489, 54,468, 66,448, 80,433, 93,425, 101,414,
                110,407, 125,405, 137,409, 146,416, 149,429, 145,440, 155,451,
                166,469, 177,487, 182,506, 172,515);
            trace(canvas, 42,510, 58,522, 75,527, 94,528, 105,532, 111,544);
            trace(canvas, 172,515, 163,528, 146,533, 132,529, 123,522);
            trace(canvas, 482,316, 481,301, 494,297, 501,283, 519,281, 528,264,
                543,263, 552,248, 570,243, 581,233, 594,232, 603,238,
                619,243, 632,251, 646,253, 661,261, 680,268, 691,279,
                703,282, 706,297, 715,302, 715,322, 704,324);
            trace(canvas, 482,316, 485,349, 484,396, 482,446, 479,486, 480,512);
            trace(canvas, 704,324, 705,359, 705,403, 706,448, 704,486, 705,507);
            trace(canvas, 729,770, 724,746, 725,697, 727,636, 730,568, 735,510,
                744,467, 754,444, 766,428, 773,410, 785,405, 792,393,
                805,396, 813,410, 827,417, 840,433, 850,451, 858,478,
                861,515, 859,590, 857,670, 858,730, 851,774, 837,785,
                810,789, 782,786, 750,780, 729,770);
            trace(canvas, 280,936, 301,922, 327,912, 356,904, 383,897, 407,889,
                433,882, 458,879, 483,884, 507,891, 532,900, 558,909,
                579,919, 605,924, 625,932);
            trace(canvas, 625,932, 606,948, 584,965, 559,984, 537,1002,
                512,1020, 489,1039, 463,1055, 439,1063, 417,1059,
                393,1054, 370,1047);
        }
        private void glow(Canvas canvas,Path outline){
            Matrix transform=new Matrix();transform.setScale(scene.getWidth()*scale/864f,scene.getHeight()*scale/1536f);transform.postTranslate(left,top);outline.transform(transform);
            highlight.setStyle(Paint.Style.STROKE);highlight.setStrokeJoin(Paint.Join.ROUND);highlight.setStrokeCap(Paint.Cap.ROUND);
            highlight.setStrokeWidth(dp(3));highlight.setColor(0x16f4c869);canvas.drawPath(outline,highlight);
            highlight.setStrokeWidth(dp(1));highlight.setColor(0x72ffe4a0);canvas.drawPath(outline,highlight);
        }
        private void trace(Canvas canvas,float... points){
            Path path=new Path();path.moveTo(points[0],points[1]);
            for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);
            glow(canvas,path);
        }
        private final Bitmap woodTexture=BitmapFactory.decodeResource(getResources(),R.drawable.wood_sign);
        private void drawWoodSign(Canvas canvas,float availableHeight){
            // The sign fills the complete space above the tavern illustration.
            if(availableHeight<=0||woodTexture==null)return;
            android.graphics.RectF band=new android.graphics.RectF(0,0,getWidth(),availableHeight);
            Paint board=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            // Center crop preserves the scale of the grain on different phone screens.
            float sourceAspect=woodTexture.getWidth()/(float)woodTexture.getHeight();
            float targetAspect=getWidth()/availableHeight;
            int sourceWidth=woodTexture.getWidth(),sourceHeight=woodTexture.getHeight();
            android.graphics.Rect source;
            if(sourceAspect>targetAspect){
                int cropped=Math.round(sourceHeight*targetAspect);
                int inset=(sourceWidth-cropped)/2;
                source=new android.graphics.Rect(inset,0,inset+cropped,sourceHeight);
            }else{
                int cropped=Math.round(sourceWidth/targetAspect);
                int inset=(sourceHeight-cropped)/2;
                source=new android.graphics.Rect(0,inset,sourceWidth,inset+cropped);
            }
            canvas.drawBitmap(woodTexture,source,band,board);
            board.setShader(new LinearGradient(0,0,0,availableHeight,
                new int[]{0x450b0805,0x00000000,0x60130b06},null,Shader.TileMode.CLAMP));
            canvas.drawRect(band,board);board.setShader(null);
            String name="La Taverne du père Rufus";
            board.setTypeface(Typeface.create("serif",Typeface.BOLD));
            board.setTextAlign(Paint.Align.CENTER);
            board.setTextSize(Math.min(dp(24),availableHeight*.34f));
            float usable=getWidth()-dp(24);
            if(board.measureText(name)>usable)board.setTextSize(board.getTextSize()*usable/board.measureText(name));
            float centerX=getWidth()/2f;
            float baseline=availableHeight/2f-(board.ascent()+board.descent())/2f;
            board.setColor(0xd9000000);canvas.drawText(name,centerX+dp(1),baseline+dp(2),board);
            board.setColor(0xffffe4b4);canvas.drawText(name,centerX,baseline,board);
        }
        @Override public boolean onTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();return true;}if(event.getAction()!=MotionEvent.ACTION_UP)return true;if(Math.abs(event.getX()-downX)>dp(28)||Math.abs(event.getY()-downY)>dp(28))return true;
            float x=(event.getX()-left)/scale/scene.getWidth(),y=(event.getY()-top)/scale/scene.getHeight();String target=null;
            if(x>.02f&&x<.26f&&y>.25f&&y<.43f)target="rumeurs";
            else if(x>.85f&&x<.99f&&y>.29f&&y<.56f)target="personnages";
            else if(x>.52f&&x<.84f&&y>.16f&&y<.40f)target="contrats";
            else if(x>.28f&&x<.79f&&y>.59f&&y<.74f)target="carte";
            if(target!=null){section=target;openCharacter=-1;openContract=-1;show();performClick();}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private void campaignScreen(){
        if(campaignCharacter<0){title("Campagne");LinearLayout introduction=panel();introduction.addView(text("Choisis ton personnage",24,INK,true));gap(introduction,10);introduction.addView(text("Ouvre son espace de campagne pour consulter sa fiche, son inventaire, ses dés et ses notes.",16,INK,false));
            List<TavernDb.Character> characters=db.characters(accountId);
            if(characters.isEmpty()){LinearLayout empty=panel();empty.addView(text("Aucun personnage pour ce compte.",17,INK,false));gap(empty,12);empty.addView(button("Créer un personnage",()->enterSection("personnages")));}
            for(TavernDb.Character c:characters){LinearLayout card=panel();card.addView(text(c.name,25,INK,true));gap(card,6);card.addView(text(characterSummary(c),15,INK,false));gap(card,12);card.addView(button("Choisir ce personnage",()->{campaignCharacter=c.id;expandedCampaignPanel=-1;show();}));}
            return;
        }
        TavernDb.Character current=null;for(TavernDb.Character c:db.characters(accountId))if(c.id==campaignCharacter)current=c;
        if(current==null){campaignCharacter=-1;expandedCampaignPanel=-1;campaignScreen();return;}
        final TavernDb.Character character=current;
        body.addView(button("‹  Changer de personnage",()->{campaignCharacter=-1;expandedCampaignPanel=-1;show();}));gap(body,16);title(character.name);
        final String[] headings={"Fiche du personnage","Inventaire","Dés","Note"};
        for(int i=0;i<headings.length;i++){
            if(expandedCampaignPanel>=0&&expandedCampaignPanel!=i)continue;
            final int index=i;LinearLayout card=panel();LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);card.addView(heading);
            TextView expand=text(expandedCampaignPanel==i?"↙":"⤢",26,INK,true);expand.setGravity(Gravity.CENTER);expand.setContentDescription(expandedCampaignPanel==i?"Réduire "+headings[i]:"Agrandir "+headings[i]);
            TextView name=text(headings[i],22,INK,true);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            heading.addView(expand,new LinearLayout.LayoutParams(dp(44),dp(44)));expand.setOnClickListener(v->{expandedCampaignPanel=expandedCampaignPanel==index?-1:index;show();});gap(card,12);
            if(i==0){card.addView(text(characterSummary(character),17,INK,false));gap(card,12);showProfile(card,ArchetypeRules.forName(character.role),character.origin,character.background);if(!character.sheet.isEmpty()){gap(card,12);card.addView(text(character.sheet,16,INK,false));}gap(card,12);card.addView(button("Ouvrir et modifier la fiche",()->{openCharacter=character.id;charactersPage=0;enterSection("personnages");}));}
            else if(i==1){EditText inventory=input(card,"Un objet et sa quantité par ligne",character.inventory,5);card.addView(button("Enregistrer l’inventaire",()->{db.setInventory(accountId,character.id,inventory.getText().toString());info("Inventaire enregistré.");}));}
            else if(i==2){LinearLayout dice=column();card.addView(dice);TextView result=text("Choisis un dé et sa quantité, puis touche D pour lancer.",18,INK,false);
                java.security.SecureRandom random=new java.security.SecureRandom();
                for(int sides:new int[]{4,6,8,10,12,20,100}){
                    final int[] count={1};LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,dp(50));rowParams.bottomMargin=dp(7);dice.addView(row,rowParams);
                    Button roll=button("D"+sides,()->{
                        int total=0;StringBuilder detail=new StringBuilder();
                        for(int n=0;n<count[0];n++){int value=1+random.nextInt(sides);total+=value;if(n>0)detail.append(" + ");detail.append(value);}
                        result.setText(count[0]+"D"+sides+" : "+detail+" = "+total);
                    });row.addView(roll,new LinearLayout.LayoutParams(0,-1,1));
                    TextView number=text("1",19,INK,true);number.setGravity(Gravity.CENTER);
                    Button less=button("‹",()->{if(count[0]>1)count[0]--;number.setText(Integer.toString(count[0]));});less.setContentDescription("Retirer un D"+sides);row.addView(less,new LinearLayout.LayoutParams(dp(44),-1));
                    row.addView(number,new LinearLayout.LayoutParams(dp(38),-1));
                    Button more=button("›",()->{if(count[0]<100)count[0]++;number.setText(Integer.toString(count[0]));});more.setContentDescription("Ajouter un D"+sides);row.addView(more,new LinearLayout.LayoutParams(dp(44),-1));
                }gap(card,8);card.addView(result);}
            else{EditText notes=input(card,"Notes de campagne…",db.campaignNotes(accountId,character.id),6);card.addView(button("Enregistrer la note",()->{db.setCampaignNotes(accountId,character.id,notes.getText().toString());info("Note enregistrée.");}));}
        }
    }
    private void rumoursScreen(){title("Rumeurs");boolean any=false;for(TavernDb.MapPlace place:db.mapPlaces())if(place.interest){any=true;LinearLayout p=panel();p.addView(text(place.description,18,INK,false));gap(p,12);p.addView(button("Voir sur la carte",()->{section="carte";show();}));}if(!any){LinearLayout p=panel();p.addView(text("Aucune rumeur pour le moment.",17,INK,false));}}
    private void showAuthentication(){
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));body=column();body.setPadding(dp(22),dp(50),dp(22),dp(30));scroll.addView(body);
        title("La Taverne");LinearLayout p=panel();p.addView(text(creatingAccount?"Créer un compte":"Bienvenue à la compagnie",25,INK,true));gap(p,12);
        p.addView(text(creatingAccount?"Choisis un pseudo et un mot de passe.":"Connecte-toi pour retrouver tes personnages.",16,0xff526056,false));gap(p,24);
        label(p,"Pseudo");EditText pseudo=input(p,"Ton pseudo","",1);pseudo.setSingleLine(true);
        label(p,"Mot de passe");EditText password=input(p,"Mot de passe","",1);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirmation=null;if(creatingAccount){label(p,"Confirmer le mot de passe");confirmation=input(p,"Répète le mot de passe","",1);confirmation.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);}
        CheckBox remember=new CheckBox(this);remember.setText("Rester connecté sur cet appareil");remember.setTextColor(INK);remember.setChecked(false);p.addView(remember);gap(p,12);
        final EditText confirm=confirmation;
        Button submit=button(creatingAccount?"Créer mon compte":"Se connecter",()->{});submit.setOnClickListener(v->{
            String name=pseudo.getText().toString().trim();String secret=password.getText().toString();
            if(name.length()<3||name.length()>32){info("Choisis un pseudo de 3 à 32 caractères.");return;}
            if(secret.length()<8){info("Le mot de passe doit contenir au moins 8 caractères.");return;}
            if(creatingAccount&&!secret.equals(confirm.getText().toString())){info("Les mots de passe ne correspondent pas.");return;}
            boolean registration=creatingAccount;submit.setEnabled(false);
            char[] chars=secret.toCharArray();password.setText("");if(confirm!=null)confirm.setText("");
            new Thread(()->{long id=-1;String error=null;try{id=registration?db.createAccount(name,chars):db.authenticate(name,chars);}catch(Exception e){error="Impossible d'accéder au compte.";}finally{Arrays.fill(chars,'\0');}
                final long result=id;final String failure=error;runOnUiThread(()->{submit.setEnabled(true);if(failure!=null){info(failure);return;}if(result<0){info(registration?"Ce pseudo est déjà utilisé.":"Pseudo ou mot de passe incorrect.");return;}accountId=result;if(remember.isChecked())getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit().putLong(REMEMBERED_ACCOUNT,result).apply();else getSharedPreferences(SESSION_PREFS,MODE_PRIVATE).edit().remove(REMEMBERED_ACCOUNT).apply();creatingAccount=false;show();});}).start();
        });p.addView(submit);gap(p,14);
        Button switchMode=button(creatingAccount?"J'ai déjà un compte":"Créer un compte",()->{creatingAccount=!creatingAccount;show();});p.addView(switchMode);
        gap(body,14);body.addView(button("Vérifier les mises à jour",()->updates.check(true)));gap(body,14);body.addView(text("Compte conservé sur ce téléphone. Garde ton mot de passe : sans adresse e-mail, il n'y a pas de récupération automatique.",14,0xffc9c8b7,false));
    }
    private void showMap(){
        List<TavernDb.MapPlace> places=db.mapPlaces();
        MapView map=new MapView(this,false,marker->{
            for(TavernDb.MapPlace place:db.mapPlaces())if(place.id==marker.id&&place.interest==marker.interest){
                if(place.interest)new AlertDialog.Builder(this).setTitle("Rumeur").setMessage(place.description).setPositiveButton("Fermer",null).show();
                else{section="contrats";openContract=place.id;show();}
                return;
            }
        });
        map.focusOnTavern();
        List<MapView.Marker> markers=new ArrayList<>();for(TavernDb.MapPlace place:places)markers.add(new MapView.Marker(place.id,place.interest,place.x,place.y));
        map.setMarkers(markers);root.addView(map,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=text("Pince pour zoomer · déplace la carte · touche un repère",13,0xffeedfbb,false);
        hint.setGravity(Gravity.CENTER);hint.setPadding(dp(8),dp(8),dp(8),dp(8));root.addView(hint);
    }
    private void chooseLocation(float[] selected,Runnable after){
        MapView map=new MapView(this,true,null);if(selected[0]>=0)map.setChosen(selected[0],selected[1]);
        LinearLayout box=column();box.setPadding(dp(12),dp(12),dp(12),dp(6));
        TextView instruction=text("Déplace et zoome la carte, puis touche le lieu voulu.",15,INK,false);box.addView(instruction);
        box.addView(map,new LinearLayout.LayoutParams(-1,dp(480)));
        new AlertDialog.Builder(this).setTitle("Choisir un lieu sur la carte").setView(box)
            .setNegativeButton("Annuler",null).setPositiveButton("Valider",(dialog,which)->{
                if(map.chosenX()<0){info("Aucun lieu choisi.");return;}
                selected[0]=map.chosenX();selected[1]=map.chosenY();after.run();
            }).show();
    }
    private void placeholderScreen(String name){title(name);LinearLayout p=panel();p.addView(text("À venir",23,INK,true));gap(p,10);p.addView(text("Cet espace sera développé plus tard.",16,INK,false));}
    private void subTabs(String first,String second,boolean firstActive,Runnable openFirst,Runnable openSecond){
        LinearLayout row=new LinearLayout(this);body.addView(row);Button left=button(first,openFirst),right=button(second,openSecond);
        left.setBackground(background(firstActive?0xff8f6b2e:0xff275340,9));right.setBackground(background(firstActive?0xff275340:0xff8f6b2e,9));
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(48),1),b=new LinearLayout.LayoutParams(0,dp(48),1);a.rightMargin=dp(5);b.leftMargin=dp(5);
        row.addView(left,a);row.addView(right,b);gap(body,16);
    }
    private void characterTabs(){subTabs("Mes personnages","Archétypes",charactersPage==0,()->{charactersPage=0;openArchetype=-1;openCharacter=-1;show();},()->{charactersPage=1;openArchetype=-1;openCharacter=-1;show();});}
    private void mjSummary(){
        title("Espace MJ");
        for(int i=0;i<MJ_TABS.length;i++){
            final int page=i;
            LinearLayout p=panel();
            Button entry=button(MJ_TABS[i]+"  ›",()->{mjPage=page;editingContractId=-1;show();});
            entry.setTextSize(18);entry.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);entry.setPadding(dp(18),0,dp(18),0);
            p.addView(entry,new LinearLayout.LayoutParams(-1,dp(56)));
        }
    }
    private void archetypeScreen(){title("Archétypes");characterTabs();for(int i=0;i<ARCHETYPES.length;i++){
        final int index=i;LinearLayout p=panel();p.addView(text(ARCHETYPES[i],23,INK,true));gap(p,8);p.addView(text(ARCHETYPE_DESCRIPTIONS[i],16,INK,false));gap(p,12);
        p.addView(button("Voir la fiche  ›",()->{openArchetype=index;show();}));
    }}
    private void archetypeDetail(int index){
        if(index<0||index>=ArchetypeRules.ALL.length){openArchetype=-1;archetypeScreen();return;}
        body.addView(button("‹  Tous les archétypes",()->{openArchetype=-1;show();}));gap(body,18);
        title(ARCHETYPES[index]);LinearLayout portrait=panel();ImageView image=new ImageView(this);
        image.setImageResource(ARCHETYPE_IMAGES[index]);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(0xfff8f0df);
        image.setContentDescription("Illustration : "+ARCHETYPES[index]);portrait.addView(image,new LinearLayout.LayoutParams(-1,dp(320)));
        gap(portrait,14);portrait.addView(text(ARCHETYPE_DESCRIPTIONS[index],17,INK,false));
        LinearLayout rules=panel();showProfile(rules,ArchetypeRules.ALL[index],"","");
    }
    private void profileRow(LinearLayout parent,String label,String value){
        LinearLayout row=new LinearLayout(this);row.setPadding(dp(9),dp(8),dp(9),dp(8));
        row.setBackground(background(0xffe2d5b9,4));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(3);parent.addView(row,lp);
        row.addView(text(label,15,INK,true),new LinearLayout.LayoutParams(0,-2,2));
        TextView content=text(value,16,INK,false);content.setGravity(Gravity.END);row.addView(content,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void showProfile(LinearLayout parent,ArchetypeRules.Profile profile,String race,String origin){
        if(profile==null){parent.addView(text("Aucune fiche d’archétype pour ce personnage.",16,INK,false));return;}
        boolean character=!race.isEmpty()&&!origin.isEmpty();
        parent.addView(text(character?"Caractéristiques du personnage":"Caractéristiques de base",20,INK,true));gap(parent,9);
        int[] values=character?ArchetypeRules.finalStats(profile,race,origin):profile.stats;
        for(int i=0;i<values.length;i++){
            String name=ArchetypeRules.ABBREVIATIONS[i]+" · "+ArchetypeRules.LABELS[i];
            int bonus=values[i]-profile.stats[i];profileRow(parent,name,values[i]+(bonus>0?" (+"+bonus+")":""));
        }
        if(character){gap(parent,8);parent.addView(text("Race : "+race+" · Origine : "+origin,14,0xff75572f,false));}
        gap(parent,15);parent.addView(text("Combat",20,INK,true));gap(parent,8);
        profileRow(parent,"Points de vie",Integer.toString(profile.pv));profileRow(parent,"Armure",Integer.toString(profile.armor));
        profileRow(parent,"Dégâts",profile.damage);profileRow(parent,"Pénétration",profile.penetration>0?Integer.toString(profile.penetration):"—");
        gap(parent,15);parent.addView(text("Compétences et capacités",20,INK,true));gap(parent,8);
        for(String ability:profile.abilities){TextView item=text("• "+ability,16,INK,false);item.setPadding(dp(5),dp(5),dp(5),dp(5));parent.addView(item);}
    }
    private Spinner characterChoice(LinearLayout parent,String title,String[] choices,String current){
        label(parent,title);ArrayList<String> values=new ArrayList<>();values.add("Choisir…");
        values.addAll(Arrays.asList(choices));int selected=values.indexOf(current);
        if(selected<0&&!current.isEmpty()){values.add(current);selected=values.size()-1;}
        Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);
        parent.addView(spinner);spinner.setSelection(Math.max(0,selected));gap(parent,16);return spinner;
    }
    private String selectedChoice(Spinner spinner){return spinner.getSelectedItemPosition()==0?"":spinner.getSelectedItem().toString();}
    private String characterSummary(TavernDb.Character character){
        ArrayList<String> parts=new ArrayList<>();for(String value:new String[]{character.origin,character.role,character.background})if(!value.isEmpty())parts.add(value);
        return parts.isEmpty()?"Fiche à compléter":android.text.TextUtils.join(" · ",parts);
    }
    private Spinner originChoice(LinearLayout parent,String current){
        Spinner spinner=characterChoice(parent,"Origine",ORIGINS,current);
        TextView description=text("Sélectionne une origine pour voir sa description.",14,0xff526056,false);parent.addView(description);gap(parent,16);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> view,View selected,int position,long id){
                String value=selectedChoice(spinner);String detail="Sélectionne une origine pour voir sa description.";
                for(int i=0;i<ORIGINS.length;i++)if(ORIGINS[i].equals(value))detail=ORIGIN_DESCRIPTIONS[i];
                description.setText(detail);
            }
            public void onNothingSelected(AdapterView<?> view){}
        });return spinner;
    }
    private void tab(LinearLayout nav,String name,String target){TextView t=text(name,14,section.equals(target)?GOLD:0xffb1bfb4,section.equals(target));t.setGravity(Gravity.CENTER);t.setPadding(dp(12),0,dp(12),0);t.setMinWidth(dp(94));nav.addView(t,new LinearLayout.LayoutParams(-2,-1));t.setOnClickListener(v->{pauseTavernAudio();section=target;openContract=-1;openCharacter=-1;editingContractId=-1;if(target.equals("personnages"))charactersPage=0;if(target.equals("mj"))mjPage=-1;show();});}
    private void contractList(){
        title("Contrats");
        LinearLayout tabs=new LinearLayout(this);body.addView(tabs);gap(body,18);
        Button active=button("En cours",()->{completedContracts=false;show();});
        Button completed=button("Contrats terminés",()->{completedContracts=true;show();});
        tabs.addView(active,new LinearLayout.LayoutParams(0,dp(48),1));
        tabs.addView(completed,new LinearLayout.LayoutParams(0,dp(48),1));
        active.setEnabled(completedContracts);completed.setEnabled(!completedContracts);
        boolean any=false;
        for(TavernDb.Contract c:db.contracts()){
            if(c.status.equals("terminé")!=completedContracts)continue;
            any=true;LinearLayout p=panel();p.addView(text(c.status.toUpperCase(Locale.FRENCH),12,0xff75572f,true));gap(p,12);p.addView(text(c.title,27,INK,true));gap(p,6);p.addView(text(c.proposer.isEmpty()?"Proposé par : non renseigné":"Proposé par : "+c.proposer,14,0xff75572f,false));gap(p,10);p.addView(text(c.description,16,0xff526056,false));gap(p,15);p.addView(text(c.count+" / "+c.places+" mercenaires  ·  Danger "+c.danger+"/5",14,INK,false));gap(p,12);p.addView(button("Voir le contrat",()->{openContract=c.id;show();}));
        }
        if(!any){LinearLayout p=panel();p.addView(text(completedContracts?"Aucun contrat terminé":"Aucun contrat en cours",21,INK,true));}
    }
    private TavernDb.Contract currentContract(){for(TavernDb.Contract c:db.contracts())if(c.id==openContract)return c;return null;}
    private Spinner characterChoice(LinearLayout p,List<TavernDb.Character> characters){label(p,"Ton personnage pour ce contrat");Spinner spinner=new Spinner(this);String[] names=new String[characters.size()];for(int i=0;i<names.length;i++)names[i]=characters.get(i).name;ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);p.addView(spinner);gap(p,15);return spinner;}
    private void contractDetail(){TavernDb.Contract c=currentContract();if(c==null){openContract=-1;contractList();return;}body.addView(button("‹  Tous les contrats",()->{openContract=-1;show();}));gap(body,20);title(c.title);LinearLayout intro=panel();intro.addView(text(c.status.toUpperCase(Locale.FRENCH)+"  ·  Danger "+c.danger+"/5",13,0xff75572f,true));gap(intro,16);intro.addView(text(c.description,18,INK,false));gap(intro,10);intro.addView(text(c.proposer.isEmpty()?"Proposé par : non renseigné":"Proposé par : "+c.proposer,14,0xff75572f,false));if(!c.reward.isEmpty()){gap(intro,14);intro.addView(text("Récompense · "+c.reward,15,0xff75572f,true));}
        List<TavernDb.MjSlot> slots=db.mjSlots(c.id);
        LinearLayout availability=panel();availability.addView(text("Disponibilités du MJ",23,INK,true));gap(availability,10);
        if(slots.isEmpty())availability.addView(text("Aucune disponibilité indiquée pour cet ancien contrat.",15,INK,false));
        for(TavernDb.MjSlot slot:slots){
            List<String> voters=db.slotVoters(slot.id);
            availability.addView(text(slotLabel(slot)+" · "+slot.votes+" vote"+(slot.votes>1?"s":""),16,INK,false));
            availability.addView(text(voters.isEmpty()?"Aucun votant":android.text.TextUtils.join(", ",voters),14,0xff526056,false));
            gap(availability,10);
        }
        List<TavernDb.Character> characters=db.characters(accountId);LinearLayout group=panel();group.addView(text("La compagnie",23,INK,true));gap(group,12);for(String n:db.participants(c.id)){group.addView(text("• "+n,16,INK,false));gap(group,5);}gap(group,10);
        if(characters.isEmpty()){group.addView(text("Crée d'abord un personnage dans ton espace personnel.",15,INK,false));}
        else{
            Spinner chosen=characterChoice(group,characters);
            CheckBox[] options=new CheckBox[slots.size()];
            if(!slots.isEmpty()){group.addView(text("Vote pour les créneaux qui te conviennent :",16,INK,true));gap(group,8);
                for(int i=0;i<slots.size();i++){TavernDb.MjSlot slot=slots.get(i);CheckBox box=new CheckBox(this);box.setText(slotLabel(slot));box.setTextColor(INK);group.addView(box);options[i]=box;}}
            Button register=button("Inscrire et voter",()->{});
            Runnable refresh=()->{TavernDb.Character ch=characters.get(chosen.getSelectedItemPosition());List<Long> votes=db.votes(c.id,ch.id);for(int i=0;i<options.length;i++)options[i].setChecked(votes.contains(slots.get(i).id));register.setText(db.isParticipant(c.id,ch.id)?"Modifier mes votes":slots.isEmpty()?"Inscrire ce personnage":"Inscrire et voter");};
            chosen.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){refresh.run();}public void onNothingSelected(AdapterView<?> parent){}});
            if(c.status.equals("ouvert")){group.addView(register);register.setOnClickListener(v->{List<Long> selected=new ArrayList<>();for(int i=0;i<options.length;i++)if(options[i].isChecked())selected.add(slots.get(i).id);if(!slots.isEmpty()&&selected.isEmpty()){info("Choisis au moins un créneau.");return;}TavernDb.Character ch=characters.get(chosen.getSelectedItemPosition());if(db.joinAndVote(c.id,ch.id,accountId,c.places,selected)){info(ch.name+" est inscrit et son vote enregistré.");show();}else info("Groupe complet ou inscription impossible.");});}
            LinearLayout chat=panel();chat.addView(text("À la table",23,INK,true));gap(chat,10);for(String m:db.messages(c.id)){chat.addView(text(m,15,INK,false));gap(chat,9);}EditText message=input(chat,"Écris à la compagnie…","",2);chat.addView(button("Envoyer",()->{String value=message.getText().toString().trim();if(value.isEmpty())return;db.addMessage(c.id,characters.get(chosen.getSelectedItemPosition()).id,accountId,value);show();}));
        }
    }
    private String timeLabel(int minute){return String.format(Locale.FRANCE,"%02d:%02d",minute/60,minute%60);}
    private String slotLabel(TavernDb.MjSlot slot){return WEEKDAYS[slot.weekday]+" · "+timeLabel(slot.startMinute)+" – "+timeLabel(slot.endMinute)+(slot.endMinute<slot.startMinute?" (lendemain)":"");}
    private void characterList(){title("Personnages");characterTabs();LinearLayout create=panel();label(create,"Nouveau personnage");EditText name=input(create,"Nom du personnage","",1);
        Spinner archetype=characterChoice(create,"Archétype de classe",ARCHETYPES,"");
        Spinner race=characterChoice(create,"Race",RACES,"");Spinner origin=originChoice(create,"");
        create.addView(button("Créer",()->{String value=name.getText().toString().trim();if(value.isEmpty()){info("Indique un nom.");return;}
            if(archetype.getSelectedItemPosition()==0||race.getSelectedItemPosition()==0||origin.getSelectedItemPosition()==0){info("Choisis un archétype, une race et une origine.");return;}
            openCharacter=db.addCharacter(accountId,value,selectedChoice(race),selectedChoice(archetype),selectedChoice(origin));show();}));
        for(TavernDb.Character c:db.characters(accountId)){LinearLayout p=panel();p.addView(text(c.name,25,INK,true));gap(p,8);
            p.addView(text(characterSummary(c),15,INK,false));gap(p,14);
            p.addView(button("Ouvrir la fiche",()->{openCharacter=c.id;show();}));}
    }
    private void characterDetail(){TavernDb.Character found=null;for(TavernDb.Character c:db.characters(accountId))if(c.id==openCharacter)found=c;final TavernDb.Character c=found;if(c==null){openCharacter=-1;characterList();return;}body.addView(button("‹  Mes personnages",()->{openCharacter=-1;show();}));gap(body,20);title(c.name);
        LinearLayout generated=panel();showProfile(generated,ArchetypeRules.forName(c.role),c.origin,c.background);
        LinearLayout sheet=panel();sheet.addView(text("Fiche du personnage",23,INK,true));gap(sheet,16);label(sheet,"Nom");EditText name=input(sheet,"Nom",c.name,1);
        Spinner archetype=characterChoice(sheet,"Archétype de classe",ARCHETYPES,c.role);
        Spinner race=characterChoice(sheet,"Race",RACES,c.origin);Spinner origin=originChoice(sheet,c.background);
        label(sheet,"Fiche et notes");EditText notes=input(sheet,"Caractéristiques, compétences, équipement…",c.sheet,7);
        LinearLayout inventoryPanel=panel();inventoryPanel.addView(text("Inventaire",23,INK,true));gap(inventoryPanel,12);EditText inventory=input(inventoryPanel,"Un objet et sa quantité par ligne",c.inventory,7);
        LinearLayout history=panel();history.addView(text("Son histoire",23,INK,true));gap(history,16);label(history,"Lore du personnage");EditText lore=input(history,"Origines, passé, relations, ambitions…",c.lore,10);
        LinearLayout attachment=panel();attachment.addView(text("Fichier de fiche",23,INK,true));gap(attachment,12);if(c.fileUri!=null){attachment.addView(button("Ouvrir le fichier joint",()->{Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse(c.fileUri));view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(view);}catch(Exception e){info("Aucune application ne peut ouvrir ce fichier.");}}));gap(attachment,10);}attachment.addView(button("Joindre un PDF ou une image",()->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("*/*");pick.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/png","image/jpeg","image/webp"});pick.addCategory(Intent.CATEGORY_OPENABLE);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(pick,PICK_SHEET);}));
        body.addView(button("Enregistrer les modifications",()->{String value=name.getText().toString().trim();if(value.isEmpty()){info("Indique un nom.");return;}
            db.updateCharacter(accountId,c.id,value,selectedChoice(race),selectedChoice(archetype),selectedChoice(origin),notes.getText().toString(),lore.getText().toString(),inventory.getText().toString());
            info("Personnage enregistré.");show();}));
    }
    private void masterScreen(){
        TavernDb.Contract editing=null;for(TavernDb.Contract c:db.contracts())if(c.id==editingContractId&&c.proposerId==accountId)editing=c;
        if(editingContractId>=0&&editing==null)editingContractId=-1;
        final TavernDb.Contract current=editing;
        if(mjPage<0){mjSummary();return;}
        title(MJ_TABS[mjPage]);
        body.addView(button("‹  Retour à l’Espace MJ",()->{mjPage=-1;editingContractId=-1;show();}));gap(body,18);
        if(mjPage==4){mjMapsScreen();return;}if(mjPage==5){myMapsScreen();return;}if(mjPage==6){mjPlaceholder("Bestiaire");return;}
        if(mjPage==1){myContractsScreen();return;}if(mjPage==3){myInterestsScreen();return;}if(mjPage==2){placeInterestScreen();return;}
        if(current!=null){body.addView(button("‹  Mes contrats",()->{editingContractId=-1;mjPage=1;show();}));gap(body,16);}
        LinearLayout p=panel();p.addView(text(current==null?"Nouveau contrat":"Modifier le contrat",23,INK,true));gap(p,16);
        label(p,"Titre");EditText contractTitle=input(p,"La bête de Rochebrune",current==null?"":current.title,1);
        label(p,"Description");EditText description=input(p,"Ce que les mercenaires savent…",current==null?"":current.description,5);
        label(p,"Récompense");EditText reward=input(p,"1 200 pièces d'or",current==null?"":current.reward,1);
        label(p,"Danger (1 à 5)");EditText danger=input(p,"2",current==null?"2":Integer.toString(current.danger),1);danger.setInputType(2);
        label(p,"Places (1 à 12)");EditText places=input(p,"4",current==null?"4":Integer.toString(current.places),1);places.setInputType(2);
        label(p,"Disponibilités du MJ · coche plusieurs jours");CheckBox[] days=new CheckBox[7];
        List<TavernDb.MjSlot> existing=current==null?new ArrayList<>():db.mjSlots(current.id);
        for(int i=0;i<7;i++){days[i]=new CheckBox(this);days[i].setText(WEEKDAYS[i]);days[i].setTextColor(INK);for(TavernDb.MjSlot slot:existing)if(slot.weekday==i)days[i].setChecked(true);p.addView(days[i]);}gap(p,12);
        p.addView(text("Plage horaire commune aux jours cochés",14,0xff526056,false));gap(p,10);
        int[] times=existing.isEmpty()?new int[]{18*60,21*60}:new int[]{existing.get(0).startMinute,existing.get(0).endMinute};
        Button start=button("Début · "+timeLabel(times[0]),()->{});p.addView(start);gap(p,8);start.setOnClickListener(v->new TimePickerDialog(this,(picker,hour,minute)->{times[0]=hour*60+minute;start.setText("Début · "+timeLabel(times[0]));},times[0]/60,times[0]%60,true).show());
        Button end=button("Fin · "+timeLabel(times[1]),()->{});p.addView(end);gap(p,16);end.setOnClickListener(v->new TimePickerDialog(this,(picker,hour,minute)->{times[1]=hour*60+minute;end.setText("Fin · "+timeLabel(times[1]));},times[1]/60,times[1]%60,true).show());
        float[] savedLocation=current==null?null:db.contractLocation(current.id);
        final float[] location=savedLocation==null?new float[]{-1,-1}:savedLocation;
        Button place=button(location[0]<0?"Placer le contrat sur la carte":"Déplacer le contrat sur la carte",()->{});p.addView(place);gap(p,14);
        place.setOnClickListener(v->chooseLocation(location,()->place.setText("Lieu choisi ✓ · toucher pour modifier")));
        p.addView(button(current==null?"Afficher le contrat":"Enregistrer les modifications",()->{
            String t=contractTitle.getText().toString().trim(),d=description.getText().toString().trim();if(t.isEmpty()||d.isEmpty()){info("Titre et description nécessaires.");return;}
            boolean[] selected=new boolean[7];boolean any=false;for(int i=0;i<7;i++){selected[i]=days[i].isChecked();any|=selected[i];}if(!any){info("Coche au moins un jour.");return;}
            if(times[0]==times[1]){info("Choisis une heure de fin différente de l’heure de début.");return;}
            if(location[0]<0){info("Place le contrat sur la carte.");return;}
            int risk=number(danger,2,1,5),capacity=number(places,4,1,12);
            if(current==null){long id=db.addContract(accountId,t,d,reward.getText().toString(),risk,capacity,selected,times[0],times[1]);db.setContractLocation(id,accountId,location[0],location[1]);}
            else{if(!db.updateContract(current.id,accountId,t,d,reward.getText().toString(),risk,capacity,selected,times[0],times[1])){info("Modification impossible.");return;}db.setContractLocation(current.id,accountId,location[0],location[1]);editingContractId=-1;}
            info(current==null?"Contrat affiché.":"Contrat modifié.");show();
        }));
        if(current!=null){gap(p,12);p.addView(button("Supprimer ce contrat",()->new AlertDialog.Builder(this).setTitle("Supprimer ce contrat ?")
            .setMessage("Les inscriptions, votes et messages de ce contrat seront aussi supprimés.")
            .setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(dialog,which)->{if(db.deleteContract(current.id,accountId)){editingContractId=-1;info("Contrat supprimé.");show();}else info("Suppression impossible.");}).show()));}
    }
    private void placeInterestScreen(){
        LinearLayout interest=panel();interest.addView(text("Placer une rumeur",23,INK,true));gap(interest,10);
        interest.addView(text("Un indice discret pour donner envie d’explorer, sans danger ni récompense.",15,0xff526056,false));gap(interest,12);
        EditText clue=input(interest,"Ex. Des lumières apparaissent parfois dans les ruines…","",3);
        final float[] interestLocation={-1,-1};
        Button interestPlace=button("Placer la rumeur sur la carte",()->{});interest.addView(interestPlace);gap(interest,10);
        interestPlace.setOnClickListener(v->chooseLocation(interestLocation,()->interestPlace.setText("Lieu choisi ✓ · toucher pour modifier")));
        interest.addView(button("Publier la rumeur",()->{
            String descriptionValue=clue.getText().toString().trim();
            if(descriptionValue.isEmpty()){info("Ajoute une courte description.");return;}
            if(interestLocation[0]<0){info("Choisis un lieu sur la carte.");return;}
            if(db.addInterest(accountId,descriptionValue,interestLocation[0],interestLocation[1])<0){info("Publication impossible.");return;}
            mjPage=3;info("Rumeur publiée.");show();
        }));
    }
    private void myInterestsScreen(){
        boolean any=false;for(TavernDb.MapPlace poi:db.mapPlaces())if(poi.interest&&poi.ownerId==accountId){any=true;
            LinearLayout p=panel();p.addView(text(poi.description,17,INK,false));gap(p,12);
            p.addView(button("Modifier cette rumeur",()->editInterest(poi)));gap(p,8);
            p.addView(button("Supprimer cette rumeur",()->new AlertDialog.Builder(this).setTitle("Supprimer cette rumeur ?")
                .setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(dialog,which)->{db.deleteInterest(poi.id,accountId);show();}).show()));
        }
        if(!any)mjPlaceholder("Aucune rumeur publiée");
    }
    private void myContractsScreen(){
        boolean any=false;for(TavernDb.Contract c:db.contracts())if(c.proposerId==accountId){any=true;
            LinearLayout p=panel();p.addView(text(c.title+" · "+c.status,18,INK,true));gap(p,12);
            p.addView(button("Modifier ou supprimer",()->{editingContractId=c.id;mjPage=0;show();}));gap(p,8);
            LinearLayout row=new LinearLayout(this);p.addView(row);
            for(String status:new String[]{"ouvert","planifié","terminé"}){Button b=button(status,()->{db.setStatus(c.id,accountId,status);show();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(2),0,dp(2),dp(12));row.addView(b,lp);}
        }
        if(!any)mjPlaceholder("Aucun contrat publié");
    }
    private void mjPlaceholder(String value){LinearLayout p=panel();p.addView(text(value,22,INK,true));gap(p,10);p.addView(text("Cet espace sera développé plus tard.",16,INK,false));}
    private void mjMapsScreen(){LinearLayout p=panel();p.addView(text("Cartes",23,INK,true));gap(p,10);p.addView(text("La carte du monde est disponible ici. Tes cartes de donjon figurent dans Mes cartes.",16,INK,false));gap(p,14);p.addView(button("Ouvrir la carte du monde",()->{section="carte";show();}));}
    private void myMapsScreen(){
        LinearLayout add=panel();add.addView(text("Mes cartes",23,INK,true));gap(add,10);add.addView(text("Ajoute une image ou un PDF de donjon depuis ton téléphone.",16,INK,false));gap(add,12);
        add.addView(button("Importer une carte",()->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("*/*");pick.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/png","image/jpeg","image/webp"});pick.addCategory(Intent.CATEGORY_OPENABLE);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(pick,PICK_MAP);}));
        for(TavernDb.DungeonMap map:db.dungeonMaps(accountId)){LinearLayout p=panel();p.addView(text(map.name,18,INK,true));gap(p,10);p.addView(button("Ouvrir",()->{Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse(map.uri));view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(view);}catch(Exception e){info("Impossible d’ouvrir cette carte.");}}));gap(p,8);p.addView(button("Supprimer",()->new AlertDialog.Builder(this).setTitle("Supprimer cette carte ?").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(dialog,which)->{db.deleteDungeonMap(accountId,map.id);show();}).show()));}
    }
    private void editInterest(TavernDb.MapPlace poi){
        LinearLayout box=column();box.setPadding(dp(18),dp(12),dp(18),dp(4));
        EditText field=input(box,"Indice",poi.description,3);
        float[] position={poi.x,poi.y};
        Button move=button("Déplacer sur la carte",()->{});box.addView(move);
        move.setOnClickListener(v->chooseLocation(position,()->move.setText("Nouvel emplacement choisi ✓")));
        new AlertDialog.Builder(this).setTitle("Modifier la rumeur").setView(box)
            .setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(dialog,which)->{
                if(!db.updateInterest(poi.id,accountId,field.getText().toString(),position[0],position[1]))info("Modification impossible.");
                else show();
            }).show();
    }
    private int number(EditText input,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(input.getText().toString())));}catch(NumberFormatException e){return fallback;}}
}
