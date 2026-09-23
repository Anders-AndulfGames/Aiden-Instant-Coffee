package com.andulf.aiden;
public class PolicyCheck {
 public static void main(String[] args) {
  check(BrewPolicy.active(true,100,337,150),"future end must not suppress live brew");
  String[] values={"b","p1","p2","d"};
  String[] stages={"Bloom","Pulse 1","Pulse 2","Drip finish"};
  for(int i=0;i<values.length;i++)check(BrewPolicy.stage(true,true,100,337,values[i],false,150+i*30).equals(stages[i]),"live progression with future end: "+values[i]);
  check(BrewPolicy.stage(true,true,100,337,"p2",false,340).equals("Complete"),"past end overrides stale pulse");
  check(BrewPolicy.stage(true,true,400,337,"b",false,410).equals("Bloom"),"new brew supersedes prior end");
  check(BrewPolicy.stage(false,true,100,337,"b",false,150).equals("Offline"),"offline");
  check(BrewPolicy.stage(true,true,100,337,"pa",false,150).equals("Paused"),"pause");
  check(BrewPolicy.stage(true,true,100,337,"b",true,150).equals("Attention"),"error");
  check(BrewPolicy.remainingSeconds(337,100000)==237,"countdown from cloud end time");
  check(BrewPolicy.remainingSeconds(337,100500)==236,"floor fractional seconds like Fellow");
  check(BrewPolicy.remainingSeconds(337,338000)==0,"no negative countdown");
  check(BrewPolicy.remainingSeconds(400,100000)==300,"revised end time changes countdown");
  check(BrewPolicy.sessionStage(true,false,100,337,"bc",false,500,-1).equals("Ready"),"startup ignores old completed brew");
  check(BrewPolicy.sessionStage(true,true,100,337,"bc",false,500,-1).equals("Ready"),"startup ignores stale brewing flag after completion");
  check(BrewPolicy.sessionStage(true,false,0,0,"",false,500,-1).equals("Ready"),"connected startup without brew history");
  check(BrewPolicy.sessionStage(false,false,100,337,"bc",false,500,-1).equals("Offline"),"offline startup must not light Ready");
  check(BrewPolicy.sessionStage(true,false,100,337,"bc",true,500,-1).equals("Attention"),"startup preserves errors");
  check(BrewPolicy.sessionStage(true,true,100,337,"b",false,150,-1).equals("Bloom"),"startup preserves an active brew");
  check(BrewPolicy.sessionStage(true,false,100,337,"bc",false,500,100).equals("Complete"),"observed brew still lights Complete");
  check(BrewPolicy.sessionStage(true,false,400,450,"bc",false,500,100).equals("Ready"),"unobserved later brew does not reuse completion tracking");
  System.out.println("PASS: 22 state, countdown and startup checks");
 }
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
