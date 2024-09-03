package analyses;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import soot.Body;
import soot.Local;
import soot.Scene;
import soot.Unit;
import soot.SootClass;
import soot.SootMethod;
import soot.SceneTransformer;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.JAddExpr;
import soot.jimple.internal.JMulExpr;
import soot.jimple.internal.JSubExpr;
import soot.jimple.toolkits.annotation.logic.*;
import soot.tagkit.BytecodeOffsetTag;
import soot.toolkits.graph.CompleteUnitGraph;
import soot.toolkits.scalar.*;

import com.microsoft.z3.*;

public class LoopAnalysis extends SceneTransformer{

	class ArrayData{
		Value arrayName;
		Boolean allReads;
		Boolean analyzable;
		HashMap<Integer, MyValue> accessExpr;

		ArrayData(Value v){
			arrayName = v;
			allReads = true;
			analyzable = true;
			accessExpr = new HashMap<>();
		}

		public String toString(){
			String ret = "{ ";
			ret += arrayName.toString();
			ret += ", ";
			ret += allReads.toString();
			ret += ", ";
			ret += analyzable.toString();
			ret += ", ";
			ret += accessExpr.toString();
			ret += " }";
			return ret;
		}
	}

	@Override
	protected void internalTransform(String arg0, Map<String, String> arg1) {
		SootClass mainClass = Scene.v().getMainClass();
		SootMethod mainMethod = mainClass.getMethodByName("main");

		System.out.println("main :");

		Body body = mainMethod.getActiveBody();
		LocalDefs ld = new SimpleLocalDefs(new CompleteUnitGraph(body));

		LoopFinder loopFinder = new LoopFinder();
		Set<Loop> loops = loopFinder.getLoops(body);

		for(Loop loop : loops){
			analyzeLoop(loop, ld);
		}
	}

	private void analyzeLoop(Loop loop, LocalDefs ld){
		List<Stmt> statements = loop.getLoopStatements();
		System.out.println("\tloop " + getBytecodeOffset(statements.get(0)) + " :");
		
		Value ind = getInductionVariable(loop);
		if(ind == null)
			return;
		
		HashMap<Value, ArrayData> arrays = new HashMap<>();

		Integer i = 0;
		for(Stmt s : statements){
			// System.out.println(s);
			if(s instanceof DefinitionStmt){
				DefinitionStmt d = (DefinitionStmt)s;
				Value left = d.getLeftOp();
				Value right = d.getRightOp();
				
				if(left instanceof ArrayRef){
					//System.out.println(d);
					ArrayRef arr = (ArrayRef)left;
					Value base = arr.getBase();
					Value index = arr.getIndex();
					DefinitionStmt def = (DefinitionStmt)ld.getDefsOfAt((Local)base, s).get(0);
					Value name = def.getRightOp();
					
					if( ! arrays.containsKey(name) )
						arrays.put(name, new ArrayData(name));
					arrays.get(name).allReads = false;
					
					MyValue v = backwardSlice(ld, index, s, ind);
					arrays.get(name).accessExpr.put(i, v);
					if(v == null){
						System.out.println("null");
						// arrays.get(name).analyzable = false;
					}
				}

				if(right instanceof ArrayRef){
					//System.out.println(d);
					ArrayRef arr = (ArrayRef)right;
					Value base = arr.getBase();
					Value index = arr.getIndex();
					DefinitionStmt def = (DefinitionStmt)ld.getDefsOfAt((Local)base, s).get(0);
					Value name = def.getRightOp();
					
					if( ! arrays.containsKey(name) )
						arrays.put(name, new ArrayData(name));
					
					MyValue v = backwardSlice(ld, index, s, ind);
					arrays.get(name).accessExpr.put(i, v);
					// System.out.println(v);
					if(v == null){
						System.out.println("null");
						// arrays.get(name).analyzable = false;
					}
				}
			}
			i++;
		}
		// System.out.println(arrays);
		i = 0;

		Value limit = getInductionLimit(loop);

		boolean skipNext = false;

		for(Stmt s : statements){
			i++;
			if(skipNext){
				skipNext = false;
				continue;
			}
			if(s instanceof DefinitionStmt){
				DefinitionStmt d = (DefinitionStmt)s;
				Value left = d.getLeftOp();
				Value right = d.getRightOp();
				
				if(left instanceof ArrayRef){
					ArrayRef arr = (ArrayRef)left;
					Value base = arr.getBase();
					
					DefinitionStmt def = (DefinitionStmt)ld.getDefsOfAt((Local)base, s).get(0);
					Value name = def.getRightOp();
					
					Integer low = getCondition(name, arrays, i-1);

					System.out.print("\t\t" + getBytecodeOffset(s) + ", ");

					MyValue rhs = backwardSlice(ld, right, s, ind);
					// System.out.println(rhs.getClass());

					if(rhs.v == null){
						if(rhs instanceof MyAddExpr)
							System.out.print("ArrayAddConstant, ");
						else if(rhs instanceof MySubExpr)
							System.out.print("ArraySubConstant, ");
						else if(rhs instanceof MyMulExpr)
							System.out.print("ArrayMulConstant, ");
					}else if(rhs.v != null){
						System.out.print("ArrayAssignConstant, ");
					}

					printCondition(low, limit);

					System.out.println(", true");
				}

				if(right instanceof ArrayRef && i < statements.size()-1){
					Stmt next = statements.get(i);
					// System.out.println(s);
					// System.out.println(next);
					if(next instanceof DefinitionStmt
						&& ((DefinitionStmt)next).getLeftOp() instanceof ArrayRef
						&& ((DefinitionStmt)next).getRightOp() == left){
						skipNext = true;

						ArrayRef arr = (ArrayRef)right;
						Value base = arr.getBase();
						
						DefinitionStmt def = (DefinitionStmt)ld.getDefsOfAt((Local)base, s).get(0);
						Value name = def.getRightOp();
						
						Integer low = getCondition(name, arrays, i-1);

						ArrayRef arr2 = (ArrayRef)((DefinitionStmt)next).getLeftOp();
						Value base2 = arr2.getBase();

						DefinitionStmt def2 = (DefinitionStmt)ld.getDefsOfAt((Local)base2, next).get(0);
						Value name2 = def2.getRightOp();
						
						Integer low2 = getCondition(name2, arrays, i);

						if(low2 < low)
							low = low2;
						// System.out.println(low);

						System.out.print("\t\t" + getBytecodeOffset(s) + ", ArrayAssignArray, ");

						printCondition(low, limit);

						System.out.println(", true");
					}
				}
			}
		}
	}
	
	private MyValue backwardSlice(LocalDefs ld, Value v, Stmt s, Value ind){
		if(v instanceof IntConstant){
			return new MyValue(v);
		}
			
		if(v == ind)
		return new MyValue(v);
		
		List<Unit> defs = ld.getDefsOfAt((Local)v, (Unit)s);

		if(defs.size() == 1){
			Stmt d = (Stmt)defs.get(0);
			if(d instanceof DefinitionStmt){
				DefinitionStmt def = (DefinitionStmt)d;
				Value right = def.getRightOp();
				if(right instanceof IntConstant){
					return new MyValue(right);
				}else if(right instanceof Local){
					return backwardSlice(ld, right, s, ind);
				}else if(right instanceof ArrayRef){
					ArrayRef a = (ArrayRef)right;
					return new MyArrayRef(new MyValue(a.getBase()), new MyValue(a.getIndex()));
				}
				
				if(right instanceof BinopExpr){
					BinopExpr b = (BinopExpr)right;
					MyValue v1 = backwardSlice(ld, b.getOp1(), s, ind);
					MyValue v2 = backwardSlice(ld, b.getOp2(), s, ind);
					if(v1 == null || v2 == null)
						return null;
					if(b instanceof JAddExpr){
						MyAddExpr r = new MyAddExpr(v1, v2);
						return r;
					} else if(b instanceof JSubExpr){
						MySubExpr r = new MySubExpr(v1, v2);
						return r;
					} else if(b instanceof JMulExpr){
						MyMulExpr r = new MyMulExpr(v1, v2);
						return r;
					} else {
						return null;
					}
				}
			}
		}
		
		return null;
	}
	
	private Value getInductionVariable(Loop l){
		Stmt s = l.getLoopStatements().get(0);
		if(s instanceof IfStmt){
			IfStmt i = (IfStmt)s;
			Value cond = i.getCondition();
			if(cond instanceof BinopExpr){
				BinopExpr bin = (BinopExpr)cond;
				return bin.getOp1();
			}
		}
		
		return null;
	}
	
	private Value getInductionLimit(Loop l){
		Stmt s = l.getLoopStatements().get(0);
		if(s instanceof IfStmt){
			IfStmt i = (IfStmt)s;
			Value cond = i.getCondition();
			if(cond instanceof BinopExpr){
				BinopExpr bin = (BinopExpr)cond;
				return bin.getOp2();
			}
		}
		
		return null;
	}

	private int getBytecodeOffset(Stmt s){
		BytecodeOffsetTag t = (BytecodeOffsetTag)(s.getTag("BytecodeOffsetTag"));
		if(t == null){
			System.out.println("bytecode offset tag not found");
			return 0;
		}
		return t.getBytecodeOffset();
	}

	private void printCondition(Integer low, Value limit){
		if(low == Integer.MAX_VALUE){
			System.out.print("true");
		}
		else if(limit instanceof IntConstant){
			Integer lint = Integer.valueOf(limit.toString());
			if(low >= lint)
				System.out.print("true");
			else
				System.out.print("false");
		}
		else{
			System.out.print(limit.toString() + " <= " + low.toString());
		}
	}
	
	class MyValue{
		Value v;
		
		MyValue(){
			v = null;
		}
		
		MyValue(Value x){
			v = x;
		}
		
		public String toString(){
			if(v == null){
				if(this instanceof MyAddExpr)
					return ((MyAddExpr)this).toString();
					else if(this instanceof MySubExpr)
					return ((MySubExpr)this).toString();
					else if(this instanceof MyMulExpr)
					return ((MyMulExpr)this).toString();
					else
					return null;
			}
			else{
				return v.toString();
			}
		}
	}
	
	class MyAddExpr extends MyValue{
		MyValue v1;
		MyValue v2;
		
		MyAddExpr(MyValue x1, MyValue x2){
			v = null;
			v1 = x1;
			v2 = x2;
		}
		
		public String toString(){
			return v1.toString() + " + " + v2.toString();
		}
	}
	
	class MySubExpr extends MyValue{
		MyValue v1;
		MyValue v2;
		
		MySubExpr(MyValue x1, MyValue x2){
			v = null;
			v1 = x1;
			v2 = x2;
		}
		
		public String toString(){
			return v1.toString() + " - " + v2.toString();
		}
	}
	
	class MyMulExpr extends MyValue{
		MyValue v1;
		MyValue v2;
		
		MyMulExpr(MyValue x1, MyValue x2){
			v = null;
			v1 = x1;
			v2 = x2;
		}

		public String toString(){
			return v1.toString() + " * " + v2.toString();
		}
	}

	class MyArrayRef extends MyValue{
		MyValue v1;
		MyValue v2;
		
		MyArrayRef(MyValue x1, MyValue x2){
			v = null;
			v1 = x1;
			v2 = x2;
		}

		public String toString(){
			return v1.toString() + "[" + v2.toString() + "]";
		}
	}

	private Integer getCondition(Value name, HashMap<Value, ArrayData> arrays, Integer i){
		ArrayData a = arrays.get(name);
		MyValue v = a.accessExpr.get(i);
		// System.out.println(i);
		if(a.allReads)
			return Integer.MAX_VALUE;
		
		Integer ret = Integer.MAX_VALUE;

		for(Map.Entry<Integer, MyValue> k : a.accessExpr.entrySet()){
			if(k.getValue() == null)
				return Integer.MIN_VALUE;
			if(k.getKey() < i){
				Integer s = solve(k.getValue(), v);
				if(s < ret)
					ret = s;
			}
			if(k.getKey() > i){
				Integer s = solve(v, k.getValue());
				if(s < ret)
					ret = s;
			}
		}

		return ret;
	}

	private Integer solve(MyValue m1, MyValue m2){
		Integer ret;
		Context ctx = new Context();
		Optimize o = ctx.mkOptimize();

		IntExpr i1 = ctx.mkIntConst("i1");
		ArithExpr e1 = generateExpr(ctx, m1, i1);
		IntExpr i2 = ctx.mkIntConst("i2");
		ArithExpr e2 = generateExpr(ctx, m2, i2);
		IntExpr zero = ctx.mkInt(0);
		// IntExpr two = ctx.mkInt(200);

		BoolExpr b1 = ctx.mkGe(i1, zero);
		BoolExpr b2 = ctx.mkGe(i2, zero);
		BoolExpr b3 = ctx.mkEq(e1, e2);
		BoolExpr b4 = ctx.mkGt(i1, i2);
		// BoolExpr b5 = ctx.mkLt(i1, two);
		
		o.MkMinimize(i1);
		o.Add(b1);
		o.Add(b2);
		o.Add(b3);
		o.Add(b4);
		// o.Add(b5);
		// System.out.println(o);
		
		if(o.Check() != Status.SATISFIABLE){
			// System.out.println("unsat");
			ret = Integer.MAX_VALUE;
		}
		else{
			// System.out.println("sat");
			Model m = o.getModel();
			// System.out.println(m.evaluate(i1, false) + " " + m.evaluate(i2, false));p
			String res = m.evaluate(i1, false).toString();
			ret = Integer.valueOf(res);
		}

		ctx.close();
		
		return ret;
	}

	private ArithExpr generateExpr(Context ctx, MyValue v, IntExpr i){
		ArithExpr ret = null;
		if(v.v == null){
			if(v instanceof MyAddExpr)
				ret = generateAddExpr(ctx, (MyAddExpr)v, i);
			if(v instanceof MySubExpr)
				ret = generateSubExpr(ctx, (MySubExpr)v, i);
			if(v instanceof MyMulExpr)
				ret = generateMulExpr(ctx, (MyMulExpr)v, i);
		}
		else{
			if(v.v instanceof IntConstant){
				IntConstant c = (IntConstant)v.v;
				ret = ctx.mkInt(c.toString());
			}
			else{
				ret = i;
			}
		}
		// System.out.println(v);
		// System.out.println(v.v);
		// System.out.println(ret);

		return ret;
	}
	
	private ArithExpr generateAddExpr(Context ctx, MyAddExpr v, IntExpr i){
		ArithExpr temp1 = generateExpr(ctx, v.v1, i);
		ArithExpr temp2 = generateExpr(ctx, v.v2, i);

		return ctx.mkAdd(temp1, temp2);
	}
	
	private ArithExpr generateSubExpr(Context ctx, MySubExpr v, IntExpr i){
		ArithExpr temp1 = generateExpr(ctx, v.v1, i);
		ArithExpr temp2 = generateExpr(ctx, v.v2, i);

		return ctx.mkSub(temp1, temp2);
	}
	
	private ArithExpr generateMulExpr(Context ctx, MyMulExpr v, IntExpr i){
		ArithExpr temp1 = generateExpr(ctx, v.v1, i);
		ArithExpr temp2 = generateExpr(ctx, v.v2, i);

		return ctx.mkMul(temp1, temp2);
	}
}
