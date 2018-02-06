// **********************************************************************
//
// Copyright (c) 2003-2018 ZeroC, Inc. All rights reserved.
//
// **********************************************************************

class Server
{
    static class LocatorI implements com.zeroc.Ice.ServantLocator
    {
        @Override
        public com.zeroc.Ice.ServantLocator.LocateResult locate(com.zeroc.Ice.Current c)
        {
            assert c.id.category.equals("book");
            return new com.zeroc.Ice.ServantLocator.LocateResult(_servant, null);
        }

        @Override
        public void finished(com.zeroc.Ice.Current c, com.zeroc.Ice.Object servant, Object cookie)
        {
        }

        @Override
        public void deactivate(String category)
        {
        }

        LocatorI(com.zeroc.Ice.Object servant)
        {
            _servant = new DispatchInterceptorI(servant);
        }

        private com.zeroc.Ice.Object _servant;
    }

    public static void main(String[] args)
    {
        int status = 0;
        java.util.List<String> extraArgs = new java.util.ArrayList<String>();

        //
        // try with resources block - communicator is automatically destroyed
        // at the end of this try block
        //
        try(com.zeroc.Ice.Communicator communicator = com.zeroc.Ice.Util.initialize(args, "config.server", extraArgs))
        {
            Runtime.getRuntime().addShutdownHook(new Thread(() ->
            {
                communicator.shutdown();
            }));

            if(!extraArgs.isEmpty())
            {
                System.err.println("too many arguments");
                status = 1;
            }
            else
            {
                status = run(communicator, extraArgs.toArray(new String[extraArgs.size()]));
            }
        }

        System.exit(status);
    }

    private static int run(com.zeroc.Ice.Communicator communicator, String[] args)
    {
        args = communicator.getProperties().parseCommandLineOptions("JDBC", args);
        com.zeroc.Ice.Properties properties = communicator.getProperties();

        String username = properties.getProperty("JDBC.Username");
        String password = properties.getProperty("JDBC.Password");
        String url = properties.getProperty("JDBC.Url");
        int nConnections = properties.getPropertyAsIntWithDefault("JDBC.NumConnections", 5);
        if(nConnections < 1)
        {
            nConnections = 1;
        }
        ConnectionPool pool = null;
        com.zeroc.Ice.Logger logger = communicator.getLogger();

        try
        {
            Class.forName(properties.getProperty("JDBC.DriverClassName")).newInstance();
        }
        catch(Exception e)
        {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            pw.flush();
            System.err.println("failed to initialize mysql driver:\n" + sw.toString());
            return 1;
        }

        try
        {
            pool = new ConnectionPool(logger, url, username, password, nConnections);
        }
        catch(java.sql.SQLException e)
        {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            pw.flush();
            System.err.println("failed to create connection pool: SQLException:\n" + sw.toString());
            return 1;
        }

        //
        // Create an object adapter
        //
        com.zeroc.Ice.ObjectAdapter adapter = communicator.createObjectAdapter("SessionFactory");

        SQLRequestContext.initialize(logger, pool);
        adapter.addServantLocator(new LocatorI(new BookI()), "book");

        adapter.add(new SessionFactoryI(logger),
                    com.zeroc.Ice.Util.stringToIdentity("SessionFactory"));
        adapter.add(new Glacier2SessionManagerI(logger),
                    com.zeroc.Ice.Util.stringToIdentity("LibrarySessionManager"));

        //
        // Everything ok, let's go.
        //
        adapter.activate();

        communicator.waitForShutdown();

        pool.destroy();

        return 0;
    }
}
