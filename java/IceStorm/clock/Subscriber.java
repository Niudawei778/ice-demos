// **********************************************************************
//
// Copyright (c) 2003-2018 ZeroC, Inc. All rights reserved.
//
// **********************************************************************

import Demo.*;

public class Subscriber
{
    public static class ClockI implements Clock
    {
        @Override
        public void tick(String date, com.zeroc.Ice.Current current)
        {
            System.out.println(date);
        }
    }

    public static void main(String[] args)
    {
        int status = 0;
        java.util.List<String> extraArgs = new java.util.ArrayList<String>();

        System.out.println("hello");
        //
        // try with resources block - communicator is automatically destroyed
        // at the end of this try block
        //
        try(com.zeroc.Ice.Communicator communicator = com.zeroc.Ice.Util.initialize(args, "config.sub", extraArgs))
        {
            System.out.print("things");
            Runtime.getRuntime().addShutdownHook(new Thread(() ->
            {
                communicator.shutdown();
            }));

            status = run(communicator, extraArgs.toArray(new String[extraArgs.size()]));
        }

        System.exit(status);
    }

    public static void usage()
    {
        System.out.println("Usage: [--batch] [--datagram|--twoway|--ordered|--oneway] " +
                           "[--retryCount count] [--id id] [topic]");
    }

    private static int run(com.zeroc.Ice.Communicator communicator, String[] args)
    {
        args = communicator.getProperties().parseCommandLineOptions("Clock", args);

        String topicName = "time";
        String option = "None";
        boolean batch = false;
        String id = null;
        String retryCount = null;
        int i;
        for(i = 0; i < args.length; ++i)
        {
            String oldoption = option;
            if(args[i].equals("--datagram"))
            {
                option = "Datagram";
            }
            else if(args[i].equals("--twoway"))
            {
                option = "Twoway";
            }
            else if(args[i].equals("--ordered"))
            {
                option = "Ordered";
            }
            else if(args[i].equals("--oneway"))
            {
                option = "Oneway";
            }
            else if(args[i].equals("--batch"))
            {
                batch = true;
            }
            else if(args[i].equals("--id"))
            {
                ++i;
                if(i >= args.length)
                {
                    usage();
                    return 1;
                }
                id = args[i];
            }
            else if(args[i].equals("--retryCount"))
            {
                ++i;
                if(i >= args.length)
                {
                    usage();
                    return 1;
                }
                retryCount = args[i];
            }
            else if(args[i].startsWith("--"))
            {
                usage();
                return 1;
            }
            else
            {
                topicName = args[i++];
                break;
            }

            if(!oldoption.equals(option) && !oldoption.equals("None"))
            {
                usage();
                return 1;
            }
        }

        if(i != args.length)
        {
            usage();
            return 1;
        }

        if(retryCount != null)
        {
            if(option.equals("None"))
            {
                option = "Twoway";
            }
            else if(!option.equals("Twoway") && !option.equals("Ordered"))
            {
                usage();
                return 1;
            }
        }

        if(batch && (option.equals("Twoway") || option.equals("Ordered")))
        {
            System.err.println("batch can only be set with oneway or datagram");
            return 1;
        }

        com.zeroc.IceStorm.TopicManagerPrx manager = com.zeroc.IceStorm.TopicManagerPrx.checkedCast(
            communicator.propertyToProxy("TopicManager.Proxy"));
        if(manager == null)
        {
            System.err.println("invalid proxy");
            return 1;
        }

        //
        // Retrieve the topic.
        //
        com.zeroc.IceStorm.TopicPrx topic;
        try
        {
            topic = manager.retrieve(topicName);
        }
        catch(com.zeroc.IceStorm.NoSuchTopic e)
        {
            try
            {
                topic = manager.create(topicName);
            }
            catch(com.zeroc.IceStorm.TopicExists ex)
            {
                System.err.println("temporary failure, try again.");
                return 1;
            }
        }

        com.zeroc.Ice.ObjectAdapter adapter = communicator.createObjectAdapter("Clock.Subscriber");

        //
        // Add a servant for the Ice object. If --id is used the
        // identity comes from the command line, otherwise a UUID is
        // used.
        //
        // id is not directly altered since it is used below to detect
        // whether subscribeAndGetPublisher can raise
        // AlreadySubscribed.
        //
        com.zeroc.Ice.Identity subId = new com.zeroc.Ice.Identity(id, "");
        if(subId.name == null)
        {
            subId.name = java.util.UUID.randomUUID().toString();
        }
        com.zeroc.Ice.ObjectPrx subscriber = adapter.add(new ClockI(), subId);

        //
        // Activate the object adapter before subscribing.
        //
        adapter.activate();

        java.util.Map<String, String> qos = new java.util.HashMap<>();
        if(retryCount != null)
        {
            qos.put("retryCount", retryCount);
        }
        //
        // Set up the proxy.
        //
        if(option.equals("Datagram"))
        {
            if(batch)
            {
                subscriber = subscriber.ice_batchDatagram();
            }
            else
            {
                subscriber = subscriber.ice_datagram();
            }
        }
        else if(option.equals("Twoway"))
        {
            // Do nothing to the subscriber proxy. Its already twoway.
        }
        else if(option.equals("Ordered"))
        {
            // Do nothing to the subscriber proxy. Its already twoway.
            qos.put("reliability", "ordered");
        }
        else if(option.equals("Oneway") || option.equals("None"))
        {
            if(batch)
            {
                subscriber = subscriber.ice_batchOneway();
            }
            else
            {
                subscriber = subscriber.ice_oneway();
            }
        }

        try
        {
            topic.subscribeAndGetPublisher(qos, subscriber);
        }
        catch(com.zeroc.IceStorm.AlreadySubscribed e)
        {
            // If we're manually setting the subscriber id ignore.
            if(id == null)
            {
                e.printStackTrace();
                return 1;
            }
            else
            {
                System.out.println("reactivating persistent subscriber");
            }
        }
        catch(com.zeroc.IceStorm.InvalidSubscriber e)
        {
            e.printStackTrace();
            return 1;
        }
        catch(com.zeroc.IceStorm.BadQoS e)
        {
            e.printStackTrace();
            return 1;
        }

        communicator.waitForShutdown();

        topic.unsubscribe(subscriber);

        return 0;
    }
}
