class Main {
	public static void main (String [] args) {
		int n = 20;
		int A[] = new int[n];
		int B[] = new int[n];

		for(int i=0; i<8; i++){
			B[i] = A[4*i];
			A[2*i + 6] -= 1;
		}
	}
}