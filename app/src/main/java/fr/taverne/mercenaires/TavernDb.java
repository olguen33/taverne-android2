package fr.taverne.mercenaires;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.json.JSONArray;
import org.json.JSONObject;

final class TavernDb extends SQLiteOpenHelper {
    private static final int HASH_ITERATIONS=600000;
    TavernDb(Context context) { super(context, "taverne.db", null, 15); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT, pseudo TEXT NOT NULL COLLATE NOCASE UNIQUE, salt BLOB NOT NULL, password_hash BLOB NOT NULL)");
        db.execSQL("CREATE TABLE characters(id INTEGER PRIMARY KEY AUTOINCREMENT, owner_id INTEGER REFERENCES accounts(id), name TEXT NOT NULL, origin TEXT NOT NULL DEFAULT '', role TEXT NOT NULL DEFAULT '', background TEXT NOT NULL DEFAULT '', sheet TEXT NOT NULL DEFAULT '', lore TEXT NOT NULL DEFAULT '', inventory TEXT NOT NULL DEFAULT '', campaign_notes TEXT NOT NULL DEFAULT '', gold INTEGER NOT NULL DEFAULT 0, file_uri TEXT)");
        db.execSQL("CREATE TABLE contracts(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, description TEXT NOT NULL, reward TEXT NOT NULL DEFAULT '', danger INTEGER NOT NULL DEFAULT 1, places INTEGER NOT NULL DEFAULT 4, status TEXT NOT NULL DEFAULT 'ouvert', paid_out INTEGER NOT NULL DEFAULT 0, locked_slot_id INTEGER, locked_date TEXT, proposer_id INTEGER REFERENCES accounts(id))");
        db.execSQL("CREATE TABLE participants(id INTEGER PRIMARY KEY AUTOINCREMENT, contract_id INTEGER NOT NULL, character_id INTEGER NOT NULL, UNIQUE(contract_id, character_id))");
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, contract_id INTEGER NOT NULL, character_id INTEGER NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE dates(id INTEGER PRIMARY KEY AUTOINCREMENT, contract_id INTEGER NOT NULL, character_id INTEGER NOT NULL, proposed_at TEXT NOT NULL, UNIQUE(contract_id, character_id, proposed_at))");
        db.execSQL("CREATE TABLE mj_dates(id INTEGER PRIMARY KEY AUTOINCREMENT, contract_id INTEGER NOT NULL, proposer_id INTEGER NOT NULL, proposed_at TEXT NOT NULL, UNIQUE(contract_id, proposer_id, proposed_at))");
        db.execSQL("CREATE TABLE mj_slots(id INTEGER PRIMARY KEY AUTOINCREMENT, contract_id INTEGER NOT NULL, proposer_id INTEGER NOT NULL, weekday INTEGER NOT NULL CHECK(weekday BETWEEN 0 AND 6), start_minute INTEGER NOT NULL, end_minute INTEGER NOT NULL, UNIQUE(contract_id, proposer_id, weekday, start_minute, end_minute))");
        db.execSQL("CREATE TABLE slot_votes(slot_id INTEGER NOT NULL, character_id INTEGER NOT NULL, PRIMARY KEY(slot_id, character_id))");
        db.execSQL("CREATE TABLE contract_locations(contract_id INTEGER PRIMARY KEY, x REAL NOT NULL, y REAL NOT NULL)");
        db.execSQL("CREATE TABLE points_of_interest(id INTEGER PRIMARY KEY AUTOINCREMENT, owner_id INTEGER NOT NULL, description TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL)");
        db.execSQL("CREATE TABLE dungeon_maps(id INTEGER PRIMARY KEY AUTOINCREMENT, owner_id INTEGER NOT NULL, name TEXT NOT NULL, uri TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2){db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT, pseudo TEXT NOT NULL COLLATE NOCASE UNIQUE, salt BLOB NOT NULL, password_hash BLOB NOT NULL)");db.execSQL("ALTER TABLE characters ADD COLUMN owner_id INTEGER REFERENCES accounts(id)");}
        if(oldVersion<3)db.execSQL("ALTER TABLE contracts ADD COLUMN proposer_id INTEGER REFERENCES accounts(id)");
        if(oldVersion<4)db.execSQL("CREATE TABLE mj_dates(id INTEGER PRIMARY KEY AUTOINCREMENT, contract_id INTEGER NOT NULL, proposer_id INTEGER NOT NULL, proposed_at TEXT NOT NULL, UNIQUE(contract_id, proposer_id, proposed_at))");
        if(oldVersion<5)db.execSQL("CREATE TABLE mj_slots(id INTEGER PRIMARY KEY AUTOINCREMENT, contract_id INTEGER NOT NULL, proposer_id INTEGER NOT NULL, weekday INTEGER NOT NULL CHECK(weekday BETWEEN 0 AND 6), start_minute INTEGER NOT NULL, end_minute INTEGER NOT NULL, UNIQUE(contract_id, proposer_id, weekday, start_minute, end_minute))");
        if(oldVersion<6)db.execSQL("CREATE TABLE slot_votes(slot_id INTEGER NOT NULL, character_id INTEGER NOT NULL, PRIMARY KEY(slot_id, character_id))");
        if(oldVersion<7){db.execSQL("CREATE TABLE contract_locations(contract_id INTEGER PRIMARY KEY, x REAL NOT NULL, y REAL NOT NULL)");db.execSQL("CREATE TABLE points_of_interest(id INTEGER PRIMARY KEY AUTOINCREMENT, owner_id INTEGER NOT NULL, description TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL)");}
        if(oldVersion<8)db.execSQL("ALTER TABLE characters ADD COLUMN background TEXT NOT NULL DEFAULT ''");
        if(oldVersion<9)db.execSQL("CREATE TABLE dungeon_maps(id INTEGER PRIMARY KEY AUTOINCREMENT, owner_id INTEGER NOT NULL, name TEXT NOT NULL, uri TEXT NOT NULL)");
        if(oldVersion<10)db.execSQL("ALTER TABLE characters ADD COLUMN inventory TEXT NOT NULL DEFAULT ''");
        if(oldVersion<11)db.execSQL("ALTER TABLE characters ADD COLUMN campaign_notes TEXT NOT NULL DEFAULT ''");
        if(oldVersion<12)db.execSQL("ALTER TABLE characters ADD COLUMN gold INTEGER NOT NULL DEFAULT 0");
        if(oldVersion<13)db.execSQL("ALTER TABLE contracts ADD COLUMN paid_out INTEGER NOT NULL DEFAULT 0");
        if(oldVersion<14)db.execSQL("ALTER TABLE contracts ADD COLUMN locked_slot_id INTEGER");
        if(oldVersion<15)db.execSQL("ALTER TABLE contracts ADD COLUMN locked_date TEXT");
    }
    private byte[] hash(char[] password,byte[] salt){PBEKeySpec spec=new PBEKeySpec(password,salt,HASH_ITERATIONS,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}catch(Exception e){throw new IllegalStateException("Impossible de protéger le mot de passe",e);}finally{spec.clearPassword();}}
    long createAccount(String pseudo,char[] password){byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);byte[] digest=hash(password,salt);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("pseudo",pseudo);v.put("salt",salt);v.put("password_hash",digest);long id=db.insertWithOnConflict("accounts",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(id<0)return -1;db.execSQL("UPDATE characters SET owner_id=? WHERE owner_id IS NULL",new Object[]{id});db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
    long authenticate(String pseudo,char[] password){try(Cursor c=getReadableDatabase().rawQuery("SELECT id,salt,password_hash FROM accounts WHERE pseudo=? COLLATE NOCASE",new String[]{pseudo})){if(!c.moveToFirst())return -1;byte[] actual=hash(password,c.getBlob(1));return MessageDigest.isEqual(actual,c.getBlob(2))?c.getLong(0):-1;}}
    String accountName(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT pseudo FROM accounts WHERE id=?",new String[]{Long.toString(id)})){return c.moveToFirst()?c.getString(0):"";}}
    long addCharacter(long ownerId,String name,String race,String archetype,String background) { ContentValues v=new ContentValues();v.put("owner_id",ownerId);v.put("name",name);v.put("origin",race);v.put("role",archetype);v.put("background",background);return getWritableDatabase().insertOrThrow("characters",null,v); }
    void updateCharacter(long ownerId,long id,String name,String race,String archetype,String background,String sheet,String lore,String inventory) { ContentValues v=new ContentValues();v.put("name",name);v.put("origin",race);v.put("role",archetype);v.put("background",background);v.put("sheet",sheet);v.put("lore",lore);v.put("inventory",inventory);getWritableDatabase().update("characters",v,"id=? AND owner_id=?",new String[]{Long.toString(id),Long.toString(ownerId)}); }
    void setInventory(long ownerId,long id,String inventory){ContentValues v=new ContentValues();v.put("inventory",inventory);getWritableDatabase().update("characters",v,"id=? AND owner_id=?",new String[]{Long.toString(id),Long.toString(ownerId)});}
    void setGold(long ownerId,long id,long gold){if(gold<0||gold>1000000000L)throw new IllegalArgumentException("Solde invalide");ContentValues v=new ContentValues();v.put("gold",gold);getWritableDatabase().update("characters",v,"id=? AND owner_id=?",new String[]{Long.toString(id),Long.toString(ownerId)});}
    boolean purchase(long ownerId,long characterId,String item,int price){
        if(price<=0||item==null||item.isEmpty()||item.contains("\n"))return false;
        SQLiteDatabase database=getWritableDatabase();database.beginTransaction();try{
            String[] args={Long.toString(characterId),Long.toString(ownerId)};
            try(Cursor c=database.rawQuery("SELECT gold,inventory FROM characters WHERE id=? AND owner_id=?",args)){
                if(!c.moveToFirst()||c.getLong(0)<price)return false;
                String inventory=c.getString(1);String[] lines=inventory.isEmpty()?new String[0]:inventory.split("\n",-1);
                boolean merged=false;String prefix=item+" ×";
                for(int i=0;i<lines.length;i++){
                    if(lines[i].equals(item)){lines[i]=prefix+"2";merged=true;break;}
                    if(lines[i].startsWith(prefix))try{int count=Integer.parseInt(lines[i].substring(prefix.length()));if(count>0&&count<Integer.MAX_VALUE){lines[i]=prefix+(count+1);merged=true;break;}}catch(NumberFormatException ignored){}
                }
                String updated=merged?android.text.TextUtils.join("\n",lines):(inventory.isEmpty()?item+" ×1":inventory+(inventory.endsWith("\n")?"":"\n")+item+" ×1");
                ContentValues values=new ContentValues();values.put("gold",c.getLong(0)-price);values.put("inventory",updated);
                if(database.update("characters",values,"id=? AND owner_id=?",args)!=1)return false;
                database.setTransactionSuccessful();return true;
            }
        }finally{database.endTransaction();}
    }
    String campaignNotes(long ownerId,long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT campaign_notes FROM characters WHERE id=? AND owner_id=?",new String[]{Long.toString(id),Long.toString(ownerId)})){return c.moveToFirst()?c.getString(0):"";}}
    void setCampaignNotes(long ownerId,long id,String notes){ContentValues v=new ContentValues();v.put("campaign_notes",notes);getWritableDatabase().update("characters",v,"id=? AND owner_id=?",new String[]{Long.toString(id),Long.toString(ownerId)});}
    void setFileUri(long ownerId,long id,String uri){ContentValues v=new ContentValues();v.put("file_uri",uri);getWritableDatabase().update("characters",v,"id=? AND owner_id=?",new String[]{Long.toString(id),Long.toString(ownerId)});}
    long addContract(long proposerId,String title,String description,String reward,int danger,int places,boolean[] days,int startMinute,int endMinute) {
        if(days.length!=7||startMinute<0||startMinute>=1440||endMinute<0||endMinute>=1440||startMinute==endMinute)throw new IllegalArgumentException("Plage horaire invalide");
        boolean selected=false;for(boolean day:days)selected|=day;if(!selected)throw new IllegalArgumentException("Choisis un jour");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            ContentValues v=new ContentValues();v.put("title",title);v.put("description",description);v.put("reward",reward);v.put("danger",danger);v.put("places",places);v.put("status","ouvert");v.put("proposer_id",proposerId);
            long id=db.insertOrThrow("contracts",null,v);
            for(int day=0;day<7;day++)if(days[day]){ContentValues slot=new ContentValues();slot.put("contract_id",id);slot.put("proposer_id",proposerId);slot.put("weekday",day);slot.put("start_minute",startMinute);slot.put("end_minute",endMinute);db.insertOrThrow("mj_slots",null,slot);}
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    private long goldReward(String reward){
        if(reward==null||!reward.trim().matches("(?i)[0-9][0-9\\s\\u00a0]*(?:\\s*(?:po|pièces?\\s+d['’]or|or))?"))throw new IllegalArgumentException("Indique une récompense en pièces d’or (ex. 1 200 po).");
        try{long amount=Long.parseLong(reward.replaceAll("[^0-9]",""));if(amount<=0||amount>1000000000L)throw new NumberFormatException();return amount;}
        catch(NumberFormatException e){throw new IllegalArgumentException("La récompense doit être comprise entre 1 et 1 milliard de pièces d’or.");}
    }
    boolean lockSlot(long contractId,long proposerId,long slotId,String date){
        LocalDate chosen;try{chosen=LocalDate.parse(date);}catch(DateTimeParseException|NullPointerException e){return false;}
        if(chosen.isBefore(LocalDate.now()))return false;
        SQLiteDatabase database=getWritableDatabase();database.beginTransaction();try{
            try(Cursor c=database.rawQuery("SELECT status,locked_slot_id,locked_date FROM contracts WHERE id=? AND proposer_id=?",new String[]{Long.toString(contractId),Long.toString(proposerId)})){
                if(!c.moveToFirst()||c.getString(0).equals("terminé")||(!c.isNull(1)&&c.getLong(1)!=slotId)||!c.isNull(2))return false;
            }
            try(Cursor c=database.rawQuery("SELECT weekday FROM mj_slots WHERE id=? AND contract_id=? AND proposer_id=?",new String[]{Long.toString(slotId),Long.toString(contractId),Long.toString(proposerId)})){if(!c.moveToFirst()||chosen.getDayOfWeek().getValue()-1!=c.getInt(0))return false;}
            ContentValues values=new ContentValues();values.put("locked_slot_id",slotId);values.put("locked_date",date);values.put("status","planifié");
            if(database.update("contracts",values,"id=? AND proposer_id=?",new String[]{Long.toString(contractId),Long.toString(proposerId)})!=1)return false;
            database.setTransactionSuccessful();return true;
        }finally{database.endTransaction();}
    }
    long setStatus(long id,long proposerId,String status){
        if(!status.equals("ouvert")&&!status.equals("terminé"))throw new IllegalArgumentException("Statut inconnu.");
        SQLiteDatabase database=getWritableDatabase();database.beginTransaction();try{
            String[] args={Long.toString(id),Long.toString(proposerId)};String reward,currentStatus;boolean paid,locked,dated;
            try(Cursor c=database.rawQuery("SELECT reward,paid_out,locked_slot_id,status,locked_date FROM contracts WHERE id=? AND proposer_id=?",args)){
                if(!c.moveToFirst())throw new IllegalArgumentException("Contrat introuvable.");reward=c.getString(0);paid=c.getInt(1)!=0;locked=!c.isNull(2);currentStatus=c.getString(3);dated=!c.isNull(4);
            }
            if(status.equals("ouvert")&&(locked||paid))throw new IllegalArgumentException("La date verrouillée ne peut pas être rouverte.");
            long distributed=0;
            if(status.equals("terminé")&&!paid){
                if((!locked||!dated)&&!currentStatus.equals("terminé"))throw new IllegalArgumentException("Choisis une date précise dans Mes contrats avant de terminer.");
                long amount=goldReward(reward);List<Long> recipients=new ArrayList<>();
                try(Cursor c=database.rawQuery("SELECT c.id FROM participants p JOIN characters c ON c.id=p.character_id WHERE p.contract_id=? ORDER BY p.id",new String[]{Long.toString(id)})){while(c.moveToNext())recipients.add(c.getLong(0));}
                if(recipients.isEmpty())throw new IllegalArgumentException("Aucun personnage inscrit : impossible de partager la récompense.");
                long share=amount/recipients.size(),remainder=amount%recipients.size();
                for(int i=0;i<recipients.size();i++){
                    long part=share+(i<remainder?1:0);long characterId=recipients.get(i);
                    try(Cursor c=database.rawQuery("SELECT gold FROM characters WHERE id=?",new String[]{Long.toString(characterId)})){
                        if(!c.moveToFirst()||c.getLong(0)>1000000000L-part)throw new IllegalArgumentException("La bourse d’un participant dépasse la limite autorisée.");
                    }
                    database.execSQL("UPDATE characters SET gold=gold+? WHERE id=?",new Object[]{part,characterId});
                }
                distributed=amount;
            }
            ContentValues values=new ContentValues();values.put("status",status);if(distributed>0)values.put("paid_out",1);
            if(database.update("contracts",values,"id=? AND proposer_id=?",args)!=1)throw new IllegalArgumentException("Contrat introuvable.");
            database.setTransactionSuccessful();return distributed;
        }finally{database.endTransaction();}
    }
    boolean updateContract(long id,long proposerId,String title,String description,String reward,int danger,int places,boolean[] days,int startMinute,int endMinute){
        if(days.length!=7||startMinute<0||startMinute>=1440||endMinute<0||endMinute>=1440||startMinute==endMinute)return false;
        boolean any=false;for(boolean day:days)any|=day;if(!any)return false;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            long lockedSlot=-1;try(Cursor c=db.rawQuery("SELECT paid_out,reward,locked_slot_id FROM contracts WHERE id=? AND proposer_id=?",new String[]{Long.toString(id),Long.toString(proposerId)})){if(!c.moveToFirst()||(c.getInt(0)!=0&&!c.getString(1).equals(reward)))return false;if(!c.isNull(2))lockedSlot=c.getLong(2);}
            ContentValues v=new ContentValues();v.put("title",title);v.put("description",description);v.put("reward",reward);v.put("danger",danger);v.put("places",places);
            if(db.update("contracts",v,"id=? AND proposer_id=?",new String[]{Long.toString(id),Long.toString(proposerId)})!=1)return false;
            // Conserve les votes des créneaux identiques ; retire ceux des créneaux supprimés.
            try(Cursor c=db.rawQuery("SELECT id,weekday,start_minute,end_minute FROM mj_slots WHERE contract_id=? AND proposer_id=?",new String[]{Long.toString(id),Long.toString(proposerId)})){
                while(c.moveToNext()){long slotId=c.getLong(0);int day=c.getInt(1);if(!days[day]||c.getInt(2)!=startMinute||c.getInt(3)!=endMinute){if(slotId==lockedSlot)return false;db.delete("slot_votes","slot_id=?",new String[]{Long.toString(slotId)});db.delete("mj_slots","id=?",new String[]{Long.toString(slotId)});}}
            }
            for(int day=0;day<7;day++)if(days[day]){ContentValues slot=new ContentValues();slot.put("contract_id",id);slot.put("proposer_id",proposerId);slot.put("weekday",day);slot.put("start_minute",startMinute);slot.put("end_minute",endMinute);db.insertWithOnConflict("mj_slots",null,slot,SQLiteDatabase.CONFLICT_IGNORE);}
            db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }
    boolean deleteContract(long id,long proposerId){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
        try(Cursor c=db.rawQuery("SELECT 1 FROM contracts WHERE id=? AND proposer_id=?",new String[]{Long.toString(id),Long.toString(proposerId)})){if(!c.moveToFirst())return false;}
        String[] args={Long.toString(id)};
        db.execSQL("DELETE FROM slot_votes WHERE slot_id IN (SELECT id FROM mj_slots WHERE contract_id=?)",new Object[]{id});
        for(String table:new String[]{"mj_slots","mj_dates","dates","messages","participants"})db.delete(table,"contract_id=?",args);
        db.delete("contract_locations","contract_id=?",args);
        db.delete("contracts","id=? AND proposer_id=?",new String[]{Long.toString(id),Long.toString(proposerId)});
        db.setTransactionSuccessful();return true;
    }finally{db.endTransaction();}}
    boolean join(long contractId,long characterId,long ownerId,int places) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try (Cursor c=db.rawQuery("SELECT COUNT(*) FROM participants WHERE contract_id=?",new String[]{Long.toString(contractId)})) {
            if(!owns(db,ownerId,characterId))return false;
            c.moveToFirst();if(c.getInt(0)>=places)return false;
            ContentValues v=new ContentValues();v.put("contract_id",contractId);v.put("character_id",characterId);
            if(db.insertWithOnConflict("participants",null,v,SQLiteDatabase.CONFLICT_IGNORE)<0)return false;
            db.setTransactionSuccessful();return true;
        } finally {db.endTransaction();}
    }
    boolean joinAndVote(long contractId,long characterId,long ownerId,int places,List<Long> selected){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            if(!owns(db,ownerId,characterId))return false;
            boolean registered;int count;
            try(Cursor c=db.rawQuery("SELECT EXISTS(SELECT 1 FROM participants WHERE contract_id=? AND character_id=?), (SELECT COUNT(*) FROM participants WHERE contract_id=?)",new String[]{Long.toString(contractId),Long.toString(characterId),Long.toString(contractId)})){c.moveToFirst();registered=c.getInt(0)!=0;count=c.getInt(1);}
            if(!registered&&count>=places)return false;
            int available=0;try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM mj_slots WHERE contract_id=?",new String[]{Long.toString(contractId)})){c.moveToFirst();available=c.getInt(0);}
            if(available>0&&selected.isEmpty())return false;
            for(Long id:selected){try(Cursor c=db.rawQuery("SELECT 1 FROM mj_slots WHERE id=? AND contract_id=?",new String[]{Long.toString(id),Long.toString(contractId)})){if(!c.moveToFirst())return false;}}
            if(!registered){ContentValues v=new ContentValues();v.put("contract_id",contractId);v.put("character_id",characterId);db.insertOrThrow("participants",null,v);}
            db.execSQL("DELETE FROM slot_votes WHERE character_id=? AND slot_id IN (SELECT id FROM mj_slots WHERE contract_id=?)",new Object[]{characterId,contractId});
            for(Long id:selected){ContentValues v=new ContentValues();v.put("slot_id",id);v.put("character_id",characterId);db.insertWithOnConflict("slot_votes",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
            db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }
    boolean isParticipant(long contractId,long characterId){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM participants WHERE contract_id=? AND character_id=?",new String[]{Long.toString(contractId),Long.toString(characterId)})){return c.moveToFirst();}}
    List<Long> votes(long contractId,long characterId){List<Long> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT v.slot_id FROM slot_votes v JOIN mj_slots s ON s.id=v.slot_id WHERE s.contract_id=? AND v.character_id=?",new String[]{Long.toString(contractId),Long.toString(characterId)})){while(c.moveToNext())out.add(c.getLong(0));}return out;}
    private boolean owns(SQLiteDatabase db,long ownerId,long characterId){try(Cursor c=db.rawQuery("SELECT 1 FROM characters WHERE id=? AND owner_id=?",new String[]{Long.toString(characterId),Long.toString(ownerId)})){return c.moveToFirst();}}
    void addMessage(long contractId,long characterId,long ownerId,String body) {if(!owns(getReadableDatabase(),ownerId,characterId))return;ContentValues v=new ContentValues();v.put("contract_id",contractId);v.put("character_id",characterId);v.put("body",body);v.put("created_at",System.currentTimeMillis());getWritableDatabase().insertOrThrow("messages",null,v);}
    void addDate(long contractId,long characterId,long ownerId,String value) {if(!owns(getReadableDatabase(),ownerId,characterId))return;ContentValues v=new ContentValues();v.put("contract_id",contractId);v.put("character_id",characterId);v.put("proposed_at",value);getWritableDatabase().insertWithOnConflict("dates",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    List<Character> characters(long ownerId) {List<Character> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,origin,role,background,sheet,lore,file_uri,inventory,gold FROM characters WHERE owner_id=? ORDER BY id DESC",new String[]{Long.toString(ownerId)})){while(c.moveToNext())out.add(new Character(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getLong(9)));}return out;}
    JSONArray exportLocalCharacters(long ownerId){
        JSONArray result=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,origin,role,background,sheet,lore,inventory,campaign_notes,gold FROM characters WHERE owner_id=? ORDER BY id",new String[]{Long.toString(ownerId)})){
            while(c.moveToNext()){
                JSONObject item=new JSONObject();
                try{item.put("legacy_id",c.getLong(0));item.put("name",c.getString(1));item.put("race",c.getString(2));
                    item.put("archetype",c.getString(3));item.put("origin",c.getString(4));item.put("sheet",c.getString(5));
                    item.put("lore",c.getString(6));item.put("inventory",c.getString(7));item.put("campaign_notes",c.getString(8));item.put("gold",c.getLong(9));}
                catch(org.json.JSONException e){throw new IllegalStateException("Export local impossible",e);}
                result.put(item);
            }
        }
        return result;
    }
    List<Contract> contracts() {List<Contract> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT c.id,c.title,c.description,c.reward,c.danger,c.places,c.status,(SELECT COUNT(*) FROM participants p WHERE p.contract_id=c.id),COALESCE(a.pseudo, ''),COALESCE(c.proposer_id,-1),COALESCE(c.locked_slot_id,-1),COALESCE(c.locked_date,'') FROM contracts c LEFT JOIN accounts a ON a.id=c.proposer_id ORDER BY c.id DESC",null)){while(c.moveToNext())out.add(new Contract(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4),c.getInt(5),c.getString(6),c.getInt(7),c.getString(8),c.getLong(9),c.getLong(10),c.getString(11)));}return out;}
    List<String> slotVoters(long slotId) {return names("SELECT COALESCE(a.pseudo, c.name) || ' (' || c.name || ')' FROM slot_votes v JOIN characters c ON c.id=v.character_id LEFT JOIN accounts a ON a.id=c.owner_id WHERE v.slot_id=? ORDER BY a.pseudo COLLATE NOCASE, c.name COLLATE NOCASE",slotId);}
    List<String> participants(long id) {return names("SELECT c.name FROM participants p JOIN characters c ON c.id=p.character_id WHERE p.contract_id=? ORDER BY p.id",id);}
    List<String> messages(long id) {return names("SELECT c.name || ' : ' || m.body FROM messages m JOIN characters c ON c.id=m.character_id WHERE m.contract_id=? ORDER BY m.id",id);}
    boolean addMjSlot(long contractId,long proposerId,int weekday,int startMinute,int endMinute){
        if(weekday<0||weekday>6||startMinute<0||startMinute>=1440||endMinute<0||endMinute>=1440||startMinute==endMinute)return false;
        SQLiteDatabase database=getWritableDatabase();try(Cursor c=database.rawQuery("SELECT 1 FROM contracts WHERE id=? AND proposer_id=?",new String[]{Long.toString(contractId),Long.toString(proposerId)})){if(!c.moveToFirst())return false;}
        ContentValues v=new ContentValues();v.put("contract_id",contractId);v.put("proposer_id",proposerId);v.put("weekday",weekday);v.put("start_minute",startMinute);v.put("end_minute",endMinute);
        return database.insertWithOnConflict("mj_slots",null,v,SQLiteDatabase.CONFLICT_IGNORE)>=0;
    }
    void removeMjSlot(long id,long proposerId){getWritableDatabase().delete("mj_slots","id=? AND proposer_id=?",new String[]{Long.toString(id),Long.toString(proposerId)});}
    List<MjSlot> mjSlots(long contractId){List<MjSlot> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT s.id,s.weekday,s.start_minute,s.end_minute,(SELECT COUNT(*) FROM slot_votes v WHERE v.slot_id=s.id) FROM mj_slots s WHERE s.contract_id=? ORDER BY s.weekday,s.start_minute",new String[]{Long.toString(contractId)})){while(c.moveToNext())out.add(new MjSlot(c.getLong(0),c.getInt(1),c.getInt(2),c.getInt(3),c.getInt(4)));}return out;}
    List<String> dates(long id) {return names("SELECT d.proposed_at || ' · ' || c.name FROM dates d JOIN characters c ON c.id=d.character_id WHERE d.contract_id=? ORDER BY d.proposed_at",id);}
    private List<String> names(String sql,long id){List<String> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{Long.toString(id)})){while(c.moveToNext())out.add(c.getString(0));}return out;}
    static final class Character {final long id,gold;final String name,origin,role,background,sheet,lore,fileUri,inventory;Character(long id,String name,String origin,String role,String background,String sheet,String lore,String fileUri,String inventory,long gold){this.id=id;this.name=name;this.origin=origin;this.role=role;this.background=background;this.sheet=sheet;this.lore=lore;this.fileUri=fileUri;this.inventory=inventory;this.gold=gold;}}
    static final class MjSlot {final long id;final int weekday,startMinute,endMinute,votes;MjSlot(long id,int weekday,int startMinute,int endMinute,int votes){this.id=id;this.weekday=weekday;this.startMinute=startMinute;this.endMinute=endMinute;this.votes=votes;}}
    static final class MapPlace {
        final long id,ownerId;final boolean interest;final String title,description;final float x,y;
        MapPlace(long id,long ownerId,boolean interest,String title,String description,float x,float y){this.id=id;this.ownerId=ownerId;this.interest=interest;this.title=title;this.description=description;this.x=x;this.y=y;}
    }
    List<MapPlace> mapPlaces(){
        List<MapPlace> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT l.contract_id,COALESCE(c.proposer_id,-1),c.title,c.description,l.x,l.y FROM contract_locations l JOIN contracts c ON c.id=l.contract_id WHERE c.status!='terminé'",null)){
            while(c.moveToNext())out.add(new MapPlace(c.getLong(0),c.getLong(1),false,c.getString(2),c.getString(3),c.getFloat(4),c.getFloat(5)));
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,owner_id,description,x,y FROM points_of_interest ORDER BY id",null)){
            while(c.moveToNext())out.add(new MapPlace(c.getLong(0),c.getLong(1),true,"Point d’intérêt",c.getString(2),c.getFloat(3),c.getFloat(4)));
        }
        return out;
    }
    float[] contractLocation(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT x,y FROM contract_locations WHERE contract_id=?",new String[]{Long.toString(id)})){
            return c.moveToFirst()?new float[]{c.getFloat(0),c.getFloat(1)}:null;
        }
    }
    boolean setContractLocation(long id,long ownerId,float x,float y){
        if(x<0||x>1||y<0||y>1)return false;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM contracts WHERE id=? AND proposer_id=?",new String[]{Long.toString(id),Long.toString(ownerId)})){if(!c.moveToFirst())return false;}
        ContentValues v=new ContentValues();v.put("contract_id",id);v.put("x",x);v.put("y",y);
        return getWritableDatabase().insertWithOnConflict("contract_locations",null,v,SQLiteDatabase.CONFLICT_REPLACE)>=0;
    }
    long addInterest(long ownerId,String description,float x,float y){
        if(description.trim().isEmpty()||x<0||x>1||y<0||y>1)return -1;
        ContentValues v=new ContentValues();v.put("owner_id",ownerId);v.put("description",description.trim());v.put("x",x);v.put("y",y);
        return getWritableDatabase().insert("points_of_interest",null,v);
    }
    boolean updateInterest(long id,long ownerId,String description,float x,float y){
        if(description.trim().isEmpty()||x<0||x>1||y<0||y>1)return false;
        ContentValues v=new ContentValues();v.put("description",description.trim());v.put("x",x);v.put("y",y);
        return getWritableDatabase().update("points_of_interest",v,"id=? AND owner_id=?",new String[]{Long.toString(id),Long.toString(ownerId)})==1;
    }
    boolean deleteInterest(long id,long ownerId){return getWritableDatabase().delete("points_of_interest","id=? AND owner_id=?",new String[]{Long.toString(id),Long.toString(ownerId)})==1;}
    static final class Contract {final long id,proposerId,lockedSlotId;final String title,description,reward,status,proposer,lockedDate;final int danger,places,count;Contract(long id,String title,String description,String reward,int danger,int places,String status,int count,String proposer,long proposerId,long lockedSlotId,String lockedDate){this.id=id;this.proposerId=proposerId;this.lockedSlotId=lockedSlotId;this.lockedDate=lockedDate;this.title=title;this.description=description;this.reward=reward;this.danger=danger;this.places=places;this.status=status;this.count=count;this.proposer=proposer;}}
    static final class DungeonMap {final long id;final String name,uri;DungeonMap(long id,String name,String uri){this.id=id;this.name=name;this.uri=uri;}}
    long addDungeonMap(long ownerId,String name,String uri){ContentValues v=new ContentValues();v.put("owner_id",ownerId);v.put("name",name);v.put("uri",uri);return getWritableDatabase().insert("dungeon_maps",null,v);}
    List<DungeonMap> dungeonMaps(long ownerId){List<DungeonMap> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,uri FROM dungeon_maps WHERE owner_id=? ORDER BY id DESC",new String[]{Long.toString(ownerId)})){while(c.moveToNext())out.add(new DungeonMap(c.getLong(0),c.getString(1),c.getString(2)));}return out;}
    void deleteDungeonMap(long ownerId,long id){getWritableDatabase().delete("dungeon_maps","id=? AND owner_id=?",new String[]{Long.toString(id),Long.toString(ownerId)});}
}
