object Example {
	def main(args:Array[String]):Unit	= {
		println("envv: " + System.getenv("TEST"));
		println("prop: " + System.getProperty("test"));
		println("args: " + args.mkString(" "));
		Thread.sleep(1000);
	}
}
